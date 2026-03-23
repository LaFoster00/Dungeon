import path from 'path';
import {fileURLToPath} from 'url';
import {promises as fs} from 'fs';
import process from 'process';
import {log, error as consoleError} from 'console';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const extensionRoot = path.resolve(__dirname, '..');
const sourceDir = path.resolve(extensionRoot, '..', 'dgir', 'compiler', 'assets', 'imports', 'Dungeon');
const targetDir = path.resolve(extensionRoot, 'resources', 'intrinsics', 'Dungeon');

async function main() {
    await ensureExists(sourceDir);

    await fs.rm(targetDir, {recursive: true, force: true});
    await fs.mkdir(path.dirname(targetDir), {recursive: true});
    await fs.cp(sourceDir, targetDir, {recursive: true});

    log(`Copied Blockly intrinsics from ${sourceDir} to ${targetDir}`);
}

async function ensureExists(filePath) {
    try {
        await fs.access(filePath);
    } catch {
        throw new Error(`Missing intrinsic source at ${filePath}`);
    }
}

main().catch((err) => {
    consoleError(err instanceof Error ? err.message : String(err));
    process.exit(1);
});




