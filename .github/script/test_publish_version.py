"""Check publication version rules and release-tag resolution."""
import unittest
from unittest.mock import patch

from publish_version import publication, resolve_version


class PublishVersionTest(unittest.TestCase):
    def test_release_and_snapshot_from_release_pom(self):
        self.assertEqual('2.0.0', resolve_version('release', '2.0.0'))
        self.assertEqual('2.0.0-SNAPSHOT', resolve_version('snapshot', '2.0.0'))

    def test_snapshot_pom_and_qualifier(self):
        self.assertEqual('2.0.0', resolve_version('release', '2.0.0-SNAPSHOT', '2.0.0'))
        self.assertEqual('2.0.0-SNAPSHOT', resolve_version('snapshot', '2.0.0-SNAPSHOT'))
        self.assertEqual('2.0.0-rc.1', resolve_version('release', '2.0.0-rc.1-SNAPSHOT', '2.0.0-rc.1'))

    def test_invalid_pom_versions(self):
        for revision in (None, '', 'v2.0.0', '02.0.0', '2.0', '2.0.0-SNAPSHOT-SNAPSHOT', '2.0.0 -DskipTests', '2.0.0\n-DskipTests', '2.0.0;id'):
            with self.subTest(revision=revision), self.assertRaises(ValueError):
                resolve_version('snapshot', revision)

    def test_unknown_kind(self):
        with self.assertRaises(ValueError):
            resolve_version('other', '2.0.0')

    def test_tag_mismatch(self):
        for tag in ('v2.0.0', '2.0.1', '2.0.0-SNAPSHOT', ''):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                resolve_version('release', '2.0.0', tag)
        with self.assertRaises(ValueError):
            resolve_version('snapshot', '2.0.0', '2.0.0')

    def test_release_uses_the_tagged_commit(self):
        with patch('publish_version.ET.parse') as parse, patch('publish_version.git', side_effect=['a' * 40, 'a' * 40]) as git:
            parse.return_value.findtext.return_value = '2.0.0'
            self.assertEqual(('2.0.0', 'a' * 40), publication('release', '2.0.0'))
            self.assertEqual(('rev-parse', '--verify', 'refs/tags/2.0.0^{commit}'), git.call_args.args)

    def test_release_from_snapshot_pom_uses_the_tag_version(self):
        with patch('publish_version.ET.parse') as parse, patch('publish_version.git', side_effect=['a' * 40, 'a' * 40]):
            parse.return_value.findtext.return_value = '2.0.0-SNAPSHOT'
            self.assertEqual(('2.0.0', 'a' * 40), publication('release', '2.0.0'))

    def test_tag_from_a_different_pom_version_is_rejected(self):
        with patch('publish_version.ET.parse') as parse, patch('publish_version.git') as git:
            parse.return_value.findtext.return_value = '1.5.2-SNAPSHOT'
            with self.assertRaises(ValueError):
                publication('release', '2.0.0')
            git.assert_not_called()

    def test_different_tagged_commit_is_rejected(self):
        with patch('publish_version.ET.parse') as parse, patch('publish_version.git', side_effect=['a' * 40, 'b' * 40]):
            parse.return_value.findtext.return_value = '2.0.0'
            with self.assertRaises(ValueError):
                publication('release', '2.0.0')

    def test_snapshot_uses_the_checked_out_commit(self):
        with patch('publish_version.ET.parse') as parse, patch('publish_version.git', return_value='a' * 40):
            parse.return_value.findtext.return_value = '2.0.0'
            self.assertEqual(('2.0.0-SNAPSHOT', 'a' * 40), publication('snapshot'))


if __name__ == '__main__':
    unittest.main()
