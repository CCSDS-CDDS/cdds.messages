#!/usr/bin/env python3

import re
import sys
from pathlib import Path

FIELD_PATTERN = re.compile(
    r'^(\s*(?:optional|required|repeated)?\s*[\w.<>]+'
    r'\s+\w+\s*=\s*)(\d+)(\s*;)'
)

def renumber_proto_file(path: Path):
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    output = []

    # Reset numbering per message
    current_number = None
    inside_message = False
    brace_depth = 0

    for line in lines:
        stripped = line.strip()

        # Detect message start
        if re.match(r'^message\s+\w+\s*\{', stripped):
            inside_message = True
            brace_depth = 1
            current_number = 1
            output.append(line)
            continue

        # Track nested braces
        if inside_message:
            brace_depth += line.count("{")
            brace_depth -= line.count("}")

            if brace_depth <= 0:
                inside_message = False
                current_number = None

        # Renumber field IDs
        if inside_message:
            match = FIELD_PATTERN.match(line)

            if match:
                prefix, old_id, suffix = match.groups()

                new_id = current_number

                # Sequence:
                # 1, 10, 20, 30, ...
                if current_number == 1:
                    current_number = 10
                else:
                    current_number += 10

                line = f"{prefix}{new_id}{suffix}\n"

        output.append(line)

    with open(path, "w", encoding="utf-8") as f:
        f.writelines(output)

    print(f"Renumbered: {path}")


def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <proto_directory>")
        sys.exit(1)

    proto_dir = Path(sys.argv[1])

    if not proto_dir.is_dir():
        print(f"Not a directory: {proto_dir}")
        sys.exit(1)

    proto_files = list(proto_dir.rglob("*.proto"))

    if not proto_files:
        print("No .proto files found.")
        return

    for proto_file in proto_files:
        renumber_proto_file(proto_file)


if __name__ == "__main__":
    main()