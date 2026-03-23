import * as vscode from 'vscode';
import { fetchLanguageConfig, BlocklyCompletionItem } from './handlers/languageProvider';
import * as path from 'path';
import sendBlocklyFile, {SendBlocklyFileOptions, stopBlocklyExecution} from './handlers/sendBlocklyFile';
import createBlocklyJavaProject from './handlers/createBlocklyJavaProject';

export const BLOCKLY_URL = () => vscode.workspace.getConfiguration('blocklyServer').get('url', 'http://localhost:8080');
export const SLEEP_AFTER_EACH_LINE = () => vscode.workspace.getConfiguration('blocklyServer').get('sleepAfterEachLine', 1000);
export const BLOCKLY_DAP_HOST = () => vscode.workspace.getConfiguration('blocklyServer').get('dapHost', '127.0.0.1');
export const BLOCKLY_DAP_PORT = () => vscode.workspace.getConfiguration('blocklyServer').get('dapPort', 4711);
export const COMPLETE_PROGRAM = () => vscode.workspace.getConfiguration('blocklyServer').get('completeProgram', false);
const NON_COMPLETE_PROGRAM_LINE_OFFSET = 9;

interface BlocklyDebugConfiguration extends vscode.DebugConfiguration {
    host?: string;
    dapPort?: number;
    sourceFileMap?: Record<string, string>;
    completeProgram?: boolean;
    wrapperLineOffset?: number;
}
const codeObjects = {
    'hero': {
        kind: vscode.CompletionItemKind.Class,
        detail: 'The hero Character that you control',
        insertText: 'hero',
        onlyInRoot: true
    },
    'Direction': {
        kind: vscode.CompletionItemKind.Enum,
        detail: 'A direction in the game',
        insertText: 'Direction',
        onlyInRoot: false
    },
    'LevelElement': {
        kind: vscode.CompletionItemKind.Enum,
        detail: 'An element of an Tile in the game',
        insertText: 'LevelElement',
        onlyInRoot: false
    },
    'loadNextLevel()': {
        kind: vscode.CompletionItemKind.Method,
        detail: 'Loads the next level in the game',
        insertText: 'loadNextLevel()',
        onlyInRoot: true
    },
    'loadLevel(index: number)': {
        kind: vscode.CompletionItemKind.Method,
        detail: 'Loads a specific level by index',
        insertText: new vscode.SnippetString('loadLevel(${1:index})'),
        onlyInRoot: true
    },
}

// Store API data for reuse
let cachedApiData: BlocklyCompletionItem[] = [];

// Function to get API data - fetches it if needed
async function getApiData(): Promise<BlocklyCompletionItem[]> {
    if (cachedApiData.length === 0) {
        for (const object of Object.keys(codeObjects)) {
            try {
                const result = await fetchLanguageConfig(object);
                cachedApiData = result.rawItems;
            } catch (error) {
                vscode.window.showErrorMessage("Failed to load Blockly language: " + error);
                return [];
            }
        }
    }
    return cachedApiData;
}

export function activate(context: vscode.ExtensionContext) {
    // Create diagnostic collection
    const diagnosticCollection = vscode.languages.createDiagnosticCollection('blockly');
    context.subscriptions.push(diagnosticCollection);

    // Register the command
    const runCommandDisposable = vscode.commands.registerCommand('blockly-code-runner.sendBlocklyFile', () => sendBlocklyFile());
    const createProjectCommandDisposable = vscode.commands.registerCommand('blockly-code-runner.createBlocklyJavaProject', () => createBlocklyJavaProject(context));
    const debugCommandDisposable = vscode.commands.registerCommand('blockly-code-runner.debugBlocklyFile', async () => {
        const editor = vscode.window.activeTextEditor;
        const options: SendBlocklyFileOptions = {
            waitForDebugger: true,
            sourceFileName: editor?.document.uri.fsPath
        };

        const sent = await sendBlocklyFile(options);
        if (!sent) {
            return;
        }

        const started = await vscode.debug.startDebugging(undefined, {
            type: 'blockly-dap',
            request: 'attach',
            name: 'Debug Blockly-Code',
            host: BLOCKLY_DAP_HOST(),
            dapPort: BLOCKLY_DAP_PORT(),
            sourceFileMap: {},
            completeProgram: options.completeProgram ?? COMPLETE_PROGRAM(),
            wrapperLineOffset: NON_COMPLETE_PROGRAM_LINE_OFFSET
        });

        if (!started) {
            vscode.window.showErrorMessage('Failed to attach Blockly debugger.');
        }
    });
    const debugConfigurationProvider = vscode.debug.registerDebugConfigurationProvider('blockly-dap', new BlocklyDebugConfigurationProvider());
    const debugAdapterDescriptorFactory = vscode.debug.registerDebugAdapterDescriptorFactory('blockly-dap', new BlocklyDebugAdapterDescriptorFactory());
    const debugAdapterTrackerFactory = vscode.debug.registerDebugAdapterTrackerFactory('blockly-dap', new BlocklyDebugAdapterTrackerFactory());


    // Register stop command
    const stopCommandDisposable = vscode.commands.registerCommand('blockly-code-runner.stopBlocklyExecution', () => stopBlocklyExecution());

    // Register the completion provider for multiple languages/extensions
    const completionProvider = vscode.languages.registerCompletionItemProvider(
        {scheme: 'file', language: 'java'},
        {
            async provideCompletionItems(document: vscode.TextDocument, position: vscode.Position) {
                const linePrefix = document.lineAt(position).text.substring(0, position.character);
                const returnedItems: vscode.CompletionItem[] = [];

                // defaults
                for (const [key, value] of Object.entries(codeObjects)) {
                    const rootChars = ['(', '{', '[', '=', ';', '||', '&&', '!', '>', '<', '+', '-', '*', '/', '%'];
                    // root objects are at the start of the line or after a rootChar
                    if (value.onlyInRoot && (linePrefix.trim().length > 0 && !rootChars.some(char => linePrefix.trim().endsWith(char)))) {
                        continue;
                    }
                    const comp = new vscode.CompletionItem(key);
                    comp.kind = value.kind;
                    comp.detail = value.detail;
                    comp.insertText = value.insertText;
                    returnedItems.push(comp);
                }

                // check if the line endswith codeObjects + '.'
                if (Object.keys(codeObjects).some(object => linePrefix.trim().endsWith(object + '.'))) {
                    const objectToFetch = Object.keys(codeObjects).find(object => linePrefix.trim().endsWith(object + '.'));
                    if (objectToFetch) {
                        const result = await fetchLanguageConfig(objectToFetch);
                        cachedApiData = result.rawItems; // Update cached data
                        return result.completionItems;
                    }
                }

                return returnedItems;
            }
        },
        '.' // Trigger on dot
    );

    // Register the hover provider
    const hoverProvider = vscode.languages.registerHoverProvider(
        { scheme: 'file', language: 'java' },
        {
            async provideHover(document: vscode.TextDocument, position: vscode.Position) {
                const wordRange = document.getWordRangeAtPosition(position);
                if (!wordRange) {
                    return null;
                }

                const word = document.getText(wordRange);

                const apiData = await getApiData();
                const apiItem = apiData.find(item => item.label === word);
                console.log('API Item:', apiItem);

                if (apiItem?.documentation) {
                    const md = new vscode.MarkdownString();
                    md.supportHtml = true;
                    md.isTrusted = true;

                    // Append the method signature as a code block
                    if (apiItem.detail) {
                        md.appendCodeblock(apiItem.detail, 'java');
                        md.appendMarkdown('\n\n');
                    }

                    // Extract and append the documentation text (without any potential method signature part)
                    let docText = apiItem.documentation.replace(/\\n/g, '\n');

                    // If the documentation starts with the method name, it might include the signature
                    // We want to skip that part since we've already added it as a code block
                    if (docText.startsWith(apiItem.label)) {
                        const endOfFirstLine = docText.indexOf('\n');
                        if (endOfFirstLine !== -1) {
                            docText = docText.substring(endOfFirstLine + 1).trim();
                        }
                    }

                    md.appendMarkdown(docText);

                    return new vscode.Hover(md, wordRange);
                }

                return null;
            }
        }
    );

    context.subscriptions.push(runCommandDisposable);
    context.subscriptions.push(createProjectCommandDisposable);
    context.subscriptions.push(debugCommandDisposable);
    context.subscriptions.push(stopCommandDisposable);
    context.subscriptions.push(completionProvider);
    context.subscriptions.push(hoverProvider);
    context.subscriptions.push(debugConfigurationProvider);
    context.subscriptions.push(debugAdapterDescriptorFactory);
    context.subscriptions.push(debugAdapterTrackerFactory);
}

class BlocklyDebugConfigurationProvider implements vscode.DebugConfigurationProvider {
    resolveDebugConfiguration(
        _: vscode.WorkspaceFolder | undefined,
        config: BlocklyDebugConfiguration
    ): vscode.DebugConfiguration {
        if (!config.type) {
            config.type = 'blockly-dap';
        }
        if (!config.request) {
            config.request = 'attach';
        }
        if (!config.name) {
            config.name = 'Debug Blockly-Code';
        }

        config.host = config.host ?? BLOCKLY_DAP_HOST();
        config.dapPort = config.dapPort ?? BLOCKLY_DAP_PORT();
        config.sourceFileMap = config.sourceFileMap ?? {};
        config.completeProgram = config.completeProgram ?? COMPLETE_PROGRAM();
        config.wrapperLineOffset =
            config.completeProgram
                ? 0
                : Math.max(0, config.wrapperLineOffset ?? NON_COMPLETE_PROGRAM_LINE_OFFSET);

        return config;
    }
}

class BlocklyDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {
    createDebugAdapterDescriptor(session: vscode.DebugSession): vscode.ProviderResult<vscode.DebugAdapterDescriptor> {
        const config = session.configuration as BlocklyDebugConfiguration;
        const host = config.host ?? BLOCKLY_DAP_HOST();
        const port = config.dapPort ?? BLOCKLY_DAP_PORT();
        return new vscode.DebugAdapterServer(port, host);
    }
}

class BlocklyDebugAdapterTrackerFactory implements vscode.DebugAdapterTrackerFactory {
    createDebugAdapterTracker(session: vscode.DebugSession): vscode.DebugAdapterTracker {
        const config = session.configuration as BlocklyDebugConfiguration;
        const sourceFileMap = config.sourceFileMap ?? {};
        const reverseMap = new Map<string, string>();
        const lineOffset = config.completeProgram
            ? 0
            : Math.max(0, config.wrapperLineOffset ?? NON_COMPLETE_PROGRAM_LINE_OFFSET);
        for (const [k, v] of Object.entries(sourceFileMap)) {
            reverseMap.set(v, k);
        }

        const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;

        return {
            onWillReceiveMessage: (message: { command?: string; arguments?: { source?: { path?: string } } }) => {
                if (message?.command === 'setBreakpoints' || message?.command === 'breakpointLocations') {
                    mapSourcePathToBlockly(message.arguments, sourceFileMap, workspaceRoot);
                }

                if (lineOffset > 0 && message?.command === 'setBreakpoints') {
                    remapSetBreakpointsRequest(message.arguments as SetBreakpointsRequestArgs, lineOffset);
                }

                if (lineOffset > 0 && message?.command === 'breakpointLocations') {
                    remapBreakpointLocationsRequest(message.arguments as BreakpointLocationsRequestArgs, lineOffset);
                }
            },
            onDidSendMessage: (message: unknown) => {
                mapSourcesInResponse(message as Record<string, unknown>, reverseMap, workspaceRoot, lineOffset);
            }
        };
    }
}

interface SetBreakpointsRequestArgs {
    breakpoints?: Array<{ line?: number; endLine?: number }>;
    lines?: number[];
    source?: { path?: string };
}

interface BreakpointLocationsRequestArgs {
    line?: number;
    endLine?: number;
    source?: { path?: string };
}

function remapSetBreakpointsRequest(args: SetBreakpointsRequestArgs | undefined, lineOffset: number) {
    if (!args || lineOffset <= 0) {
        return;
    }

    for (const bp of args.breakpoints ?? []) {
        bp.line = addLineOffset(bp.line, lineOffset);
        bp.endLine = addLineOffset(bp.endLine, lineOffset);
    }

    if (Array.isArray(args.lines)) {
        args.lines = args.lines.map((line) => addLineOffset(line, lineOffset) ?? line);
    }
}

function remapBreakpointLocationsRequest(args: BreakpointLocationsRequestArgs | undefined, lineOffset: number) {
    if (!args || lineOffset <= 0) {
        return;
    }

    args.line = addLineOffset(args.line, lineOffset);
    args.endLine = addLineOffset(args.endLine, lineOffset);
}

function mapSourcePathToBlockly(
    args: { source?: { path?: string } } | undefined,
    sourceFileMap: Record<string, string>,
    workspaceRoot?: string
) {
    const sourcePath = args?.source?.path;
    if (!sourcePath || sourcePath === '<unknown>') {
        return;
    }

    const mapped = sourceFileMap[sourcePath];
    if (mapped) {
        args!.source!.path = mapped;
        return;
    }

    if (path.isAbsolute(sourcePath)) {
        return;
    }

    if (!workspaceRoot) {
        return;
    }

    const candidate = path.join(workspaceRoot, sourcePath);
    const reverse = sourceFileMap[candidate];
    if (reverse) {
        args!.source!.path = reverse;
    }
}

function mapSourcesInResponse(
    message: Record<string, unknown>,
    reverseMap: Map<string, string>,
    workspaceRoot?: string,
    lineOffset = 0
) {
    if (!message) {
        return;
    }

    if (message.type === 'response' && message.command === 'stackTrace') {
        const body = message.body as {
            stackFrames?: Array<{ source?: { path?: string; name?: string }; line?: number; endLine?: number }>;
        } | undefined;
        const frames = body?.stackFrames ?? [];
        for (const frame of frames) {
            mapSource(frame.source, reverseMap, workspaceRoot);
            if (lineOffset > 0) {
                frame.line = subtractLineOffset(frame.line, lineOffset);
                frame.endLine = subtractLineOffset(frame.endLine, lineOffset);
            }
        }
    }

    if (message.type === 'response' && message.command === 'setBreakpoints') {
        const body = message.body as {
            breakpoints?: Array<{ source?: { path?: string; name?: string }; line?: number; endLine?: number }>;
        } | undefined;
        const breakpoints = body?.breakpoints ?? [];
        for (const bp of breakpoints) {
            mapSource(bp.source, reverseMap, workspaceRoot);
            if (lineOffset > 0) {
                bp.line = subtractLineOffset(bp.line, lineOffset);
                bp.endLine = subtractLineOffset(bp.endLine, lineOffset);
            }
        }
    }

    if (message.type === 'response' && message.command === 'breakpointLocations') {
        const body = message.body as {
            breakpoints?: Array<{ line?: number; endLine?: number }>;
        } | undefined;
        const breakpoints = body?.breakpoints ?? [];
        for (const bp of breakpoints) {
            if (lineOffset > 0) {
                bp.line = subtractLineOffset(bp.line, lineOffset);
                bp.endLine = subtractLineOffset(bp.endLine, lineOffset);
            }
        }
    }
}

function addLineOffset(line: number | undefined, offset: number): number | undefined {
    if (typeof line !== 'number') {
        return line;
    }
    return line + offset;
}

function subtractLineOffset(line: number | undefined, offset: number): number | undefined {
    if (typeof line !== 'number') {
        return line;
    }
    return Math.max(1, line - offset);
}

function mapSource(
    source: { path?: string; name?: string } | undefined,
    reverseMap: Map<string, string>,
    workspaceRoot?: string
) {
    if (!source?.path) {
        return;
    }

    if (source.path === '<unknown>') {
        delete source.path;
        source.name = source.name ?? '<unknown>';
        return;
    }

    const mapped = reverseMap.get(source.path);
    if (mapped) {
        source.path = mapped;
        return;
    }

    if (!path.isAbsolute(source.path) && workspaceRoot) {
        source.path = path.join(workspaceRoot, source.path);
    }
}
