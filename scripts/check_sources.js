// Lightweight static checks that do not require the Android SDK.
// 1. Every R.string.* reference in Kotlin exists in every values/strings.xml.
// 2. Resource references in the manifest resolve to files in res/.
// 3. XML resources are well formed (basic tag balance check).
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const appDir = path.join(root, "app", "src", "main");

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

const kotlinFiles = walk(path.join(appDir, "java")).filter((f) => f.endsWith(".kt"));
const libboxFiles = fs.existsSync(path.join(appDir, "libbox"))
  ? walk(path.join(appDir, "libbox")).filter((f) => f.endsWith(".kt"))
  : [];

const stringFiles = [
  path.join(appDir, "res", "values", "strings.xml"),
  path.join(appDir, "res", "values-en", "strings.xml"),
];

function parseStringNames(file) {
  const content = fs.readFileSync(file, "utf8");
  const names = new Set();
  for (const match of content.matchAll(/<string\s+name="([^"]+)"/g)) names.add(match[1]);
  return names;
}

const stringSets = stringFiles.map((file) => ({ file, names: parseStringNames(file) }));
const declared = new Set();
for (const { names } of stringSets) for (const name of names) declared.add(name);

const problems = [];

for (const file of [...kotlinFiles, ...libboxFiles]) {
  const content = fs.readFileSync(file, "utf8");
  for (const match of content.matchAll(/\bR\.string\.([A-Za-z0-9_]+)/g)) {
    const name = match[1];
    if (!declared.has(name)) {
      problems.push(`${path.relative(root, file)}: missing string resource "${name}"`);
      continue;
    }
    for (const set of stringSets) {
      if (!set.names.has(name)) {
        problems.push(
          `${path.relative(root, file)}: "${name}" missing from ${path.relative(root, set.file)}`,
        );
      }
    }
  }
}

// Manifest / resource references
const manifest = fs.readFileSync(path.join(appDir, "AndroidManifest.xml"), "utf8");
for (const match of manifest.matchAll(/@(drawable|mipmap|color|style|string)\/([A-Za-z0-9_.]+)/g)) {
  const [, type, name] = match;
  let found = false;
  if (type === "string") {
    found = declared.has(name);
  } else if (type === "color") {
    const colors = fs.readFileSync(path.join(appDir, "res", "values", "colors.xml"), "utf8");
    found = new RegExp(`<color\\s+name="${name}"`).test(colors);
  } else if (type === "style") {
    const themes = fs.readFileSync(path.join(appDir, "res", "values", "themes.xml"), "utf8");
    found = new RegExp(`<style\\s+name="${name}"`).test(themes);
  } else {
    const candidates = walk(path.join(appDir, "res")).filter((f) =>
      f.replace(/\\/g, "/").includes(`/${type}-`) || f.includes(`${type}`),
    );
    found = candidates.some((candidate) => path.basename(candidate).startsWith(`${name}.`));
  }
  if (!found) problems.push(`AndroidManifest.xml: unresolved @${type}/${name}`);
}

// Very small XML well-formedness check (comments, declarations and self closing tags aware)
function checkXml(file) {
  const content = fs.readFileSync(file, "utf8");
  const stack = [];
  const tagRegex = /<(\/?)([A-Za-z_][\w:.-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)(\/?)>/g;
  let match;
  while ((match = tagRegex.exec(content)) !== null) {
    const [, closing, tag, attrs, selfClosing] = match;
    if (attrs.trim().startsWith("?")) continue;
    if (closing) {
      const open = stack.pop();
      if (open !== tag) return `mismatched </${tag}> (open: ${open ?? "none"})`;
    } else if (!selfClosing) {
      stack.push(tag);
    }
  }
  return stack.length ? `unclosed <${stack[stack.length - 1]}>` : null;
}

const xmlFiles = walk(path.join(appDir, "res")).filter((f) => f.endsWith(".xml"));
xmlFiles.push(path.join(appDir, "AndroidManifest.xml"));
for (const file of xmlFiles) {
  const error = checkXml(file);
  if (error) problems.push(`${path.relative(root, file)}: ${error}`);
}

// Unbalanced braces (rough) for Kotlin files
for (const file of [...kotlinFiles, ...libboxFiles]) {
  const content = fs.readFileSync(file, "utf8");
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (const char of content) {
    if (inString) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') inString = false;
      continue;
    }
    if (char === '"') inString = true;
    else if (char === "{") depth++;
    else if (char === "}") depth--;
    if (depth < 0) {
      problems.push(`${path.relative(root, file)}: unexpected "}"`);
      depth = 0;
      break;
    }
  }
  if (depth !== 0) problems.push(`${path.relative(root, file)}: unbalanced braces (${depth})`);
}

console.log(`Checked ${kotlinFiles.length + libboxFiles.length} Kotlin files and ${xmlFiles.length} XML files.`);
if (problems.length) {
  console.log(`\n${problems.length} problem(s):`);
  for (const problem of problems) console.log(" - " + problem);
  process.exitCode = 1;
} else {
  console.log("No problems found.");
}
