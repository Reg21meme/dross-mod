"""Reads `jfr print --events jdk.ExecutionSample,jdk.NativeMethodSample --stack-depth 40` output (stdin) and sums, for
one thread: its samples, the top frame of each (self), every mod frame on a stack (inclusive), and a few marker frames.
Usage: jfr print ... | jfrstat.py <thread name>"""
import re
import sys
from collections import Counter

want = sys.argv[1]
samples = 0
native = 0
mod_any = 0
self_frames = Counter()
mod_inclusive = Counter()
marks = Counter()
MARKS = ["LevelRenderer.renderLevel", "LevelRenderer.renderChunkLayer", "LevelRenderer.renderEntity", "EntityRenderDispatcher.render",
         "GeoEntityRenderer", "GeoRenderer", "ParticleEngine.render", "ParticleEngine.tick", "ChunkRenderDispatcher", "uploadAllPendingUploads",
         "LevelRenderer.compileChunks", "RenderSystem.flipFrame", "glfwSwapBuffers", "GameRenderer.render", "ClientLevel.tickEntities",
         "Minecraft.tick", "renderSky", "FogRenderer", "LightTexture", "LevelRenderer.setupRender", "applyFrustum", "BlockEntityRenderDispatcher"]


def flush(kind, thread, frames):
    global samples, native, mod_any
    if thread != want or not frames:
        return
    samples += 1
    if kind == "jdk.NativeMethodSample":
        native += 1
    self_frames[frames[0]] += 1
    seen = set()
    for f in frames:
        if f.startswith("com.reg21meme") and f not in seen:
            mod_inclusive[f] += 1
            seen.add(f)
    if seen:
        mod_any += 1
    joined = " ".join(frames)
    for m in MARKS:
        if m in joined:
            marks[m] += 1


kind = None
thread = None
frames = []
for line in sys.stdin:
    line = line.rstrip("\n")
    m = re.match(r"^(jdk\.\w+) \{", line)
    if m:
        kind, thread, frames = m.group(1), None, []
        continue
    m = re.match(r'^\s+sampledThread = "([^"]*)"', line)
    if m:
        thread = m.group(1)
        continue
    m = re.match(r"^\s+([\w$.<>/]+)\(.*\)\s+line:", line)
    if m:
        frames.append(m.group(1).replace("/", "."))
        continue
    if line.startswith("}"):
        flush(kind, thread, frames)
        kind, thread, frames = None, None, []

print("thread", want, "samples", samples, "native", native)
print("self (top frame):")
for f, c in self_frames.most_common(18):
    print("  %5.1f%%  %s" % (100.0 * c / max(1, samples), f))
print("on the stack:")
for f, c in marks.most_common():
    print("  %5.1f%%  %s" % (100.0 * c / max(1, samples), f))
print("mod frames (inclusive):")
for f, c in mod_inclusive.most_common(15):
    print("  %5.1f%%  %s" % (100.0 * c / max(1, samples), f))
print('any mod frame on the stack: %.1f%%' % (100.0 * mod_any / max(1, samples)))
