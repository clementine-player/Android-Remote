#!/usr/bin/env python3
"""Compare the app's copy of remotecontrolmessages.proto with Clementine's.

Both files are compiled with protoc and compared structurally (message fields
and enum values by number), so formatting and package differences are ignored.

  * Things Clementine has that the app lacks are reported as warnings: the app
    simply ignores them, but they are features it could pick up.
  * Anything the app relies on that Clementine changed or removed is an error:
    a field or enum value with the same number but a different name, type or
    label, or one that no longer exists.

Usage: check-proto-drift.py APP_PROTO CLEMENTINE_PROTO
Requires protoc on PATH (or $PROTOC) and the Python protobuf package.
"""

import os
import subprocess
import sys
import tempfile

from google.protobuf import descriptor_pb2


def load(proto_path):
    with tempfile.NamedTemporaryFile(suffix=".pb") as out:
        subprocess.run(
            [os.environ.get("PROTOC", "protoc"),
             "-I", os.path.dirname(os.path.abspath(proto_path)),
             "--descriptor_set_out=" + out.name,
             os.path.basename(proto_path)],
            check=True)
        fds = descriptor_pb2.FileDescriptorSet()
        fds.ParseFromString(out.read())
    (file_proto,) = fds.file
    return file_proto


def short_type(field):
    if field.type_name:
        return field.type_name.rsplit(".", 1)[-1]
    return descriptor_pb2.FieldDescriptorProto.Type.Name(field.type)


def flatten(file_proto):
    """Map "Message.field#number" / "Enum.VALUE#number" to a comparable signature."""
    entries = {}

    def enum(prefix, e):
        for v in e.value:
            entries[(prefix + e.name, v.number)] = ("enum", v.name)

    def message(prefix, m):
        name = prefix + m.name
        for f in m.field:
            label = descriptor_pb2.FieldDescriptorProto.Label.Name(f.label)
            entries[(name, f.number)] = ("field", f.name, short_type(f), label)
        for e in m.enum_type:
            enum(name + ".", e)
        for nested in m.nested_type:
            message(name + ".", nested)

    for e in file_proto.enum_type:
        enum("", e)
    for m in file_proto.message_type:
        message("", m)
    return entries


def main(app_path, clementine_path):
    app = flatten(load(app_path))
    upstream = flatten(load(clementine_path))
    errors, warnings = [], []

    for key, sig in sorted(app.items()):
        scope, number = key
        if key not in upstream:
            errors.append(f"{scope} #{number} {sig[1]}: removed from Clementine")
        elif upstream[key] != sig:
            errors.append(f"{scope} #{number}: app has {sig[1:]}, Clementine has {upstream[key][1:]}")

    for key, sig in sorted(upstream.items()):
        if key not in app:
            scope, number = key
            warnings.append(f"{scope} #{number} {sig[1]}: only in Clementine")

    for w in warnings:
        print(f"::warning title=Proto drift::{w}" if os.environ.get("GITHUB_ACTIONS") else f"warning: {w}")
    for e in errors:
        print(f"::error title=Proto incompatibility::{e}" if os.environ.get("GITHUB_ACTIONS") else f"error: {e}")
    print(f"{len(errors)} incompatibilities, {len(warnings)} additions in Clementine not used by the app")
    return 1 if errors else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
