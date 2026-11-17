"""Resolve Maven publication versions from the POM and an optional release tag."""
import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


VERSION = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?')


def resolve_version(kind, revision, tag=None):
    revision = revision or ''
    base = revision[:-9] if revision.endswith('-SNAPSHOT') else revision
    if base.endswith('-SNAPSHOT') or not VERSION.fullmatch(base):
        raise ValueError('POM revision must be a version such as 2.0.0 or 2.0.0-SNAPSHOT')
    if kind not in ('release', 'snapshot'):
        raise ValueError('Unknown publication kind: ' + kind)
    if tag is not None and (kind != 'release' or tag != base):
        raise ValueError('Release tag must equal the POM version without -SNAPSHOT')
    return base + '-SNAPSHOT' if kind == 'snapshot' else base


def git(*args):
    return subprocess.check_output(['git', *args], text=True, stderr=subprocess.STDOUT).strip()


def publication(kind, tag=None):
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    revision = ET.parse('pom.xml').findtext('m:properties/m:revision', namespaces=namespace)
    version = resolve_version(kind, revision, tag)
    commit = git('rev-parse', '--verify', 'HEAD')
    if tag is not None:
        tagged = git('rev-parse', '--verify', 'refs/tags/' + tag + '^{commit}')
        if tagged != commit:
            raise ValueError('Checkout must point to the release tag')
    return version, commit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=('release', 'snapshot'))
    parser.add_argument('--tag')
    parser.add_argument('--github-output', type=Path)
    args = parser.parse_args()
    try:
        version, commit = publication(args.kind, args.tag)
    except (ValueError, ET.ParseError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, str(error) + '\n')
    if args.github_output:
        with args.github_output.open('a', encoding='utf-8') as output:
            output.write('revision=' + version + '\ncommit=' + commit + '\n')
    print('Publish ' + version + ' at ' + commit)


if __name__ == '__main__':
    main()
