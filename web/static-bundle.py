import struct
from pathlib import Path
import json

# 打包所有静态资源

# 测试目录是否存在

static = Path("./dist/static")

if not static.exists():
    print("静态资源目录不存在，请在 web 目录下运行此脚本")
    exit(1)

index = {}
bundle = bytearray()
total_length = 0  # 单位：字节

# 生成索引
for file in static.glob("**/*"):
    if file.is_file():
        relative_path = file.relative_to(static)
        print(f"添加文件: {relative_path}")
        with file.open("rb") as f:
            content = f.read()
            length = len(content)
            index[str(relative_path)] = {"d": total_length, "l": length}
            total_length += length
            bundle.extend(content)

final = bytearray()

index = json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
final.extend(b"sMbXwRlD")  # 文件头
final.extend(struct.pack("<I", len(index)))  # 写入索引长度，四字节小端序无符号整数
final.extend(index)  # 写入索引内容
final.extend(struct.pack("<I", total_length))  # 写入总长度，四字节小端序无符号整数
final.extend(bundle)  # 写入所有静态资源内容

"""
# 删除原有静态资源
for file in static.glob("**/*"):
    if file.is_file():
        file.unlink()

for folder in static.glob("**/*"):
    if folder.is_dir():
        folder.rmdir()

static.rmdir()  # 删除空目录
"""

# 写入打包后的文件
output_file = static.parent / "static-cc9fff6d.bundle"
with output_file.open("wb") as f:
    f.write(final)
