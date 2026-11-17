"""Publish to a user-selected Maven repository using local Maven credentials."""
import argparse
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

from publish_version import resolve_version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=('release', 'snapshot'))
    parser.add_argument('--alt-repo', required=True, metavar='ID::URL',
                        help='Maven altDeploymentRepository, with credentials in settings.xml')
    parser.add_argument('--settings', type=Path, help='Use a local Maven settings.xml')
    parser.add_argument('--maven', help='Maven executable, defaults to the project wrapper')
    parser.add_argument('--sign', action='store_true', help='Enable the sign profile')
    parser.add_argument('--dry-run', action='store_true', help='Run verify instead of deploy')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        server, separator, url = args.alt_repo.partition('::')
        if not server or not separator or not url:
            raise ValueError('--alt-repo must use the Maven ID::URL format')
        namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
        revision = ET.parse(root / 'pom.xml').findtext('m:properties/m:revision', namespaces=namespace)
        version = resolve_version(args.kind, revision)
        wrapper = root / ('mvnw.cmd' if os.name == 'nt' else 'mvnw')
        command = [args.maven or str(wrapper), '-B', '-ntp']
        if args.settings:
            command.extend(['--settings', str(args.settings.expanduser().resolve())])
        if args.sign:
            command.append('-Psign')
        command.extend(['-Drevision=' + version, '-DaltDeploymentRepository=' + args.alt_repo,
                        'verify' if args.dry_run else 'deploy'])
        print('Run Maven ' + command[-1] + ' for ' + version, flush=True)
        return subprocess.call(command, cwd=root)
    except (ValueError, ET.ParseError, OSError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    raise SystemExit(main())
