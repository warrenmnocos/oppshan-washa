# SVG icon conventions

File icons follow a common template: a grey document body with a white folded corner, a colored accent (band, fold, or label), and a short white text label naming the file kind. Folder icons use a two-layer orange folder with a tab. Action icons (chevrons, close, delete, edit, download) follow whatever the surrounding component needs.

Extension → icon mapping lives in `resolveFileIcon(name: string)` in `src/app/misc/utils.ts`; it returns the key suffix used by the `file-*.svg` files below, or `null` for unknown.

## File icon palette

| Icon | Accent |
|---|---|
| `directory.svg` | orange `#e8a735` / `#f4b74a` two-layer folder |
| `file-pdf.svg` | red |
| `file-document.svg` | blue |
| `file-spreadsheet.svg` | green |
| `file-presentation.svg` | orange |
| `file-image.svg` | green |
| `file-audio.svg` | purple |
| `file-video.svg` | deep orange |
| `file-archive.svg` | brown |
| `file-markdown.svg` | slate |
| `file-script.svg` | deep purple |
| `file-text.svg` | blue-grey |
| `file-crypto.svg` | coral |
| `file-executable.svg` | blue-grey 900 (`#263238`) — Windows, macOS, Linux installers / binaries |
| `file-library.svg` | indigo (`#3949ab`) — `.dll`, `.so`, `.dylib`, kernel modules, object files |
| `file-font.svg` | pink (`#c2185b`) — `.ttf`, `.otf`, `.woff`, `.woff2`, `.eot` |
| `file-ebook.svg` | olive (`#827717`) — `.epub`, `.mobi`, `.azw`, `.azw3`, `.fb2`, `.lit` |
| `file-database.svg` | amber (`#f9a825`) — `.db`, `.sqlite`, `.mdb`, `.accdb`, `.parquet`, `.avro` |
| `file-disk.svg` | dark teal (`#00695c`) — `.iso`, `.img`, `.vhd`, `.vmdk`, `.qcow2`, `.ova` |
| `file-model.svg` | cyan (`#0097a7`) — 3D models: `.stl`, `.fbx`, `.gltf`, `.glb`, `.blend`, `.usdz` |
| `file-design.svg` | purple 800 (`#6a1b9a`) — `.psd`, `.ai`, `.eps`, `.fig`, `.sketch`, `.xd`, `.indd` |
| `file.svg` | grey (unknown) |

## Adding a new file icon

1. Pick an accent color that doesn't already map to a different file family. The body and folded corner stay the standard grey + white.
2. Save as `file-<kind>.svg` in this directory. Match the path/viewBox structure of the closest existing icon — most use a 64×64 viewBox and a four-path body.
3. Extend `resolveFileIcon` in `src/app/misc/utils.ts` to return `<kind>` for the relevant extensions. Keep the extension list sorted; group by family if multiple icons share a theme.
4. Add a row to the table above so the palette stays self-documenting.