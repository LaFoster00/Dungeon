import * as vscode from 'vscode';
import * as path from 'path';
import {promises as fs} from 'fs';

const COMMAND_ID = 'blockly-code-runner.createBlocklyJavaProject';
const BUNDLED_INTRINSIC_DIR = path.join('resources', 'intrinsics', 'Dungeon');

export default async function createBlocklyJavaProject(context: vscode.ExtensionContext): Promise<void> {
    const workspaceFolder = await pickWorkspaceFolder();
    if (!workspaceFolder) {
        vscode.window.showErrorMessage('Please open a folder in VS Code before creating a Blockly project.');
        return;
    }

    const projectRoot = workspaceFolder.uri.fsPath;

    const intrinsicSourceDir = await findIntrinsicSourceDir(context);
    if (!intrinsicSourceDir) {
        vscode.window.showErrorMessage('Could not locate bundled Blockly intrinsic files. Rebuild or reinstall the extension package.');
        return;
    }

    const srcDir = path.join(projectRoot, 'src');
    const intrinsicTargetDir = path.join(srcDir, 'Dungeon');
    await fs.mkdir(intrinsicTargetDir, {recursive: true});

    const copiedFiles = await copyIntrinsicFiles(intrinsicSourceDir, intrinsicTargetDir);
    const mainFilePath = path.join(srcDir, 'Main.java');
    const createdMainFile = await writeMainFileIfMissing(mainFilePath);

    const messages: string[] = [];
    messages.push(`Initialized Blockly project structure in ${projectRoot}.`);
    messages.push(`Copied ${copiedFiles} intrinsic file${copiedFiles === 1 ? '' : 's'}.`);
    if (createdMainFile) {
        messages.push('Added starter file src/Main.java.');
    }

    vscode.window.showInformationMessage(messages.join(' '));
}

async function pickWorkspaceFolder(): Promise<vscode.WorkspaceFolder | undefined> {
    const folders = vscode.workspace.workspaceFolders;
    if (!folders || folders.length === 0) {
        return undefined;
    }

    if (folders.length === 1) {
        return folders[0];
    }

    const picked = await vscode.window.showWorkspaceFolderPick({
        placeHolder: 'Select the workspace folder to initialize'
    });
    return picked;
}

async function findIntrinsicSourceDir(
    context: vscode.ExtensionContext
): Promise<string | undefined> {
    const bundledPath = context.asAbsolutePath(BUNDLED_INTRINSIC_DIR);
    if (await pathExists(bundledPath)) {
        return bundledPath;
    }

    return undefined;
}

async function copyIntrinsicFiles(sourceDir: string, targetDir: string): Promise<number> {
    const files = await fs.readdir(sourceDir);
    // Remove the _index.txt file
    files.splice(files.indexOf('_index.txt'), 1);

    let copied = 0;
    for (const fileName of files) {
        const safeFileName = path.basename(fileName);
        const from = path.join(sourceDir, safeFileName);
        const to = path.join(targetDir, safeFileName);

        await fs.copyFile(from, to);
        copied += 1;
    }

    return copied;
}

async function writeMainFileIfMissing(mainFilePath: string): Promise<boolean> {
    if (await pathExists(mainFilePath)) {
        return false;
    }

    const mainFile = [
        'import Dungeon.*;',
        '',
        'public class Main {',
        '    public static void main() {',
        '        // Replace with your Blockly solution logic.',
        '        Hero.move();',
        '    }',
        '}',
        ''
    ].join('\n');

    await fs.writeFile(mainFilePath, mainFile, 'utf8');
    return true;
}

async function pathExists(targetPath: string): Promise<boolean> {
    try {
        await fs.access(targetPath);
        return true;
    } catch {
        return false;
    }
}

export {COMMAND_ID as createBlocklyJavaProjectCommandId};


