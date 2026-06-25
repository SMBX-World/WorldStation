import struct
from pathlib import Path
import json
import hashlib

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
for file in sorted(static.glob("**/*")):
    if file.is_file():
        relative_path = str(file.relative_to(static)).replace("\\", "/")
        print(f"添加文件: {relative_path}")
        with file.open("rb") as f:
            content = f.read()
            length = len(content)
            index[relative_path] = {"d": total_length, "l": length}
            total_length += length
            bundle.extend(content)

data = bytearray()

index_json = json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
data.extend(b"sMbXwRlD")  # 文件头 - 魔法咒语
data.extend(struct.pack("<I", len(index_json)))  # 写入索引长度，四字节小端序无符号整数
data.extend(index_json)  # 写入索引内容
data.extend(struct.pack("<I", total_length))  # 写入总长度，四字节小端序无符号整数
data.extend(bundle)  # 写入所有静态资源内容

# 计算内容 hash 作为文件名
hash = hashlib.md5(data).hexdigest()[:8]
bundle_name = f"static-{hash}.bundle"

# 写入打包后的文件
output_file = static.parent / bundle_name
with output_file.open("wb") as f:
    f.write(data)

print(f"打包完成: {bundle_name}")

# 将 bundle URL 写入 sw.js（替换占位符）
sw_path = static.parent / "sw.js"
if sw_path.exists():
    sw_content = sw_path.read_text(encoding="utf-8")
    sw_content = sw_content.replace("__BUNDLE_URL__", f"/{bundle_name}")
    sw_path.write_text(sw_content, encoding="utf-8")
    print(f"已更新 sw.js 中的 bundle URL: /{bundle_name}")

# 清理旧 bundle 文件
for old in static.parent.glob("static-*.bundle"):
    if old.name != bundle_name:
        old.unlink()
        print(f"已删除旧 bundle: {old.name}")
