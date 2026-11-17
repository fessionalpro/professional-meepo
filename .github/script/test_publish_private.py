"""Check private publication arguments and Maven process behavior."""
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

import publish_private


class PublishPrivateTest(unittest.TestCase):
    def run_main(self, *arguments):
        with patch.object(sys, 'argv', ['publish_private.py', *arguments]), patch('publish_private.ET.parse') as parse:
            parse.return_value.findtext.return_value = '2.0.0'
            return publish_private.main()

    def test_release_uses_wrapper_and_native_repository_override(self):
        with patch('publish_private.subprocess.call', return_value=0) as call:
            self.assertEqual(0, self.run_main('release', '--alt-repo', 'private::https://repo.example/releases'))
            command = call.call_args.args[0]
            root = Path(publish_private.__file__).resolve().parents[2]
            self.assertEqual(root, call.call_args.kwargs['cwd'])
            self.assertIn(Path(command[0]).name, ('mvnw', 'mvnw.cmd'))
            self.assertIn('-Drevision=2.0.0', command)
            self.assertIn('-DaltDeploymentRepository=private::https://repo.example/releases', command)
            self.assertEqual('deploy', command[-1])
            self.assertNotIn('-Paltrh', command)

    def test_snapshot_dry_run_with_signing_and_custom_maven_settings(self):
        with patch('publish_private.subprocess.call', return_value=0) as call:
            self.assertEqual(0, self.run_main('snapshot', '--alt-repo', 'private::file:///tmp/maven repo',
                                            '--maven', 'mvn', '--settings', 'local settings.xml', '--sign', '--dry-run'))
            command = call.call_args.args[0]
            self.assertEqual('mvn', command[0])
            self.assertEqual(str(Path('local settings.xml').resolve()), command[command.index('--settings') + 1])
            self.assertIn('-Psign', command)
            self.assertIn('-Drevision=2.0.0-SNAPSHOT', command)
            self.assertIn('-DaltDeploymentRepository=private::file:///tmp/maven repo', command)
            self.assertEqual('verify', command[-1])

    def test_maven_failure_is_returned(self):
        with patch('publish_private.subprocess.call', return_value=7):
            self.assertEqual(7, self.run_main('release', '--alt-repo', 'private::https://repo.example'))

    def test_invalid_repository_fails_before_maven(self):
        for repository in ('', 'https://repo.example', '::https://repo.example', 'private::'):
            with self.subTest(repository=repository), patch('publish_private.subprocess.call') as call:
                with self.assertRaises(SystemExit) as error:
                    self.run_main('release', '--alt-repo', repository)
                self.assertEqual(1, error.exception.code)
                call.assert_not_called()

    def test_missing_maven_is_reported(self):
        with patch('publish_private.subprocess.call', side_effect=FileNotFoundError('missing Maven')):
            with self.assertRaises(SystemExit) as error:
                self.run_main('release', '--alt-repo', 'private::https://repo.example')
            self.assertEqual(1, error.exception.code)

    def test_cli_help_is_available(self):
        script = Path(publish_private.__file__).resolve()
        result = subprocess.run([sys.executable, str(script), '--help'], cwd=script.parent,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.assertEqual(0, result.returncode)
        self.assertIn('--alt-repo', result.stdout)


if __name__ == '__main__':
    unittest.main()
