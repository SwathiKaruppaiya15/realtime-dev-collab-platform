const fs = require("fs");
const path = require("path");

const base = path.join(__dirname, "server", "src");

// helper to create folder
function createDir(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
    console.log("Created:", dirPath);
  }
}

// helper to create file
function createFile(filePath, content = "") {
  if (!fs.existsSync(filePath)) {
    fs.writeFileSync(filePath, content);
    console.log("File:", filePath);
  }
}

// -------------------- ROOT STRUCTURE --------------------

const structure = [
  "config",
  "modules",
  "shared/middleware",
  "shared/utils",
  "shared/constants",
  "models",
  "sockets",
];

// create base folders
structure.forEach((folder) => createDir(path.join(base, folder)));

// -------------------- CONFIG FILES --------------------

createFile(path.join(base, "config", "db.js"));
createFile(path.join(base, "config", "redis.js"));
createFile(path.join(base, "config", "env.js"));

// -------------------- AUTH MODULE --------------------

const authBase = path.join(base, "modules", "auth");

const authFolders = [
  "controllers",
  "services",
  "routes",
  "middleware",
  "validators",
  "utils",
];

authFolders.forEach((folder) =>
  createDir(path.join(authBase, folder))
);

// auth files
createFile(
  path.join(authBase, "controllers", "auth.controller.js")
);
createFile(
  path.join(authBase, "services", "auth.service.js")
);
createFile(
  path.join(authBase, "routes", "auth.routes.js")
);
createFile(
  path.join(authBase, "middleware", "auth.middleware.js")
);
createFile(
  path.join(authBase, "middleware", "role.middleware.js")
);
createFile(
  path.join(authBase, "validators", "auth.validator.js")
);
createFile(
  path.join(authBase, "utils", "token.util.js")
);
createFile(
  path.join(authBase, "utils", "hash.util.js")
);
createFile(path.join(authBase, "index.js"));

// -------------------- OTHER MODULES (EMPTY) --------------------

const otherModules = [
  "user",
  "room",
  "chat",
  "editor",
  "presence",
  "version",
  "webrtc",
];

otherModules.forEach((module) => {
  const modulePath = path.join(base, "modules", module);
  createDir(modulePath);
});

// -------------------- SHARED --------------------

createFile(
  path.join(base, "shared", "middleware", "error.middleware.js")
);
createFile(
  path.join(base, "shared", "utils", "logger.js")
);

// -------------------- GLOBAL FILES --------------------

createFile(path.join(base, "models", "index.js"));
createFile(path.join(base, "sockets", "index.js"));
createFile(path.join(base, "app.js"));
createFile(path.join(base, "server.js"));

console.log("\n✅ Structure created successfully.");