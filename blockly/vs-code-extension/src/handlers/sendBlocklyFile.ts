import * as vscode from "vscode";
import axios from "axios";
import * as path from "path";
import {promises as fs} from "fs";
import {showMessageWithTimeout} from "../utils/utils";
import {BLOCKLY_URL, SLEEP_AFTER_EACH_LINE} from "../extension";

// Create a diagnostic collection to manage error diagnostics
const diagnosticCollection =
    vscode.languages.createDiagnosticCollection("blockly");
const wrapperOffset = 9; // Offset for the wrapper code in Java

export interface SendBlocklyFileOptions {
    waitForDebugger?: boolean;
    sourceFileName?: string;
}

export async function resolveCompleteProgramMode(
    document?: vscode.TextDocument,
): Promise<boolean> {
    if (!document) {
        return false;
    }

    return hasDungeonIntrinsicsNextToSourceFile(document);
}

export default async function sendBlocklyFile(
    options: SendBlocklyFileOptions = {},
): Promise<boolean> {
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
        vscode.window.showErrorMessage("No active editor detected!");
        return false;
    }

    if (editor.document.languageId !== "java") {
        vscode.window.showErrorMessage("Please open a Java file!");
        return false;
    }

    if (editor.document.isDirty) {
        const result = await editor.document.save();
        if (!result) {
            vscode.window.showErrorMessage("Failed to save the Java file!");
            return false;
        }
    }

    // Clear previous diagnostics
    diagnosticCollection.clear();

    const code: string = editor.document.getText();
    let completeProgramMode = false;

    try {
        const queryParams = new URLSearchParams();
        queryParams.set("sleep", String(SLEEP_AFTER_EACH_LINE()));

        if (options.waitForDebugger) {
            queryParams.set("waitForDebugger", "1");
        }

        completeProgramMode = await resolveCompleteProgramMode(editor.document);
        if (completeProgramMode) {
            queryParams.set("complete", "1");
        }

        const sourceFileName =
            options.sourceFileName ?? editor.document.uri.fsPath;
        if (sourceFileName) {
            queryParams.set("sourceFileName", sourceFileName);
        }

        await axios.post(
            BLOCKLY_URL() + "/reset",
            {},
            {headers: {"Content-Type": "text/plain"}},
        ); // reset before any input
        await axios.post(
            BLOCKLY_URL() + "/code?" + queryParams.toString(),
            code,
            {headers: {"Content-Type": "text/plain"}},
        );

        if (options.waitForDebugger) {
            showMessageWithTimeout(
                "Blockly file sent. Waiting for debugger attach...",
            );
        } else {
            showMessageWithTimeout("Blockly file sent successfully!");
        }
        return true;
    } catch (error: unknown) {
        if (!axios.isAxiosError(error)) throw error; // rethrow if not an AxiosError

        const axiosError = error;

        if (axiosError.response?.status.toString().startsWith("4")) {
            const errorMessage = String(axiosError.response.data ?? "");
            vscode.window.showErrorMessage("Execution failed");

            if (
                errorMessage ===
                "Another code execution is already running. Please stop it first."
            ) {
                vscode.window.showErrorMessage(errorMessage);
                return false;
            }

            // Parse and display the error messages
            const amountOfErrors = displayErrorsInEditor(
                errorMessage,
                editor.document,
                completeProgramMode,
            );
            if (amountOfErrors === 0) {
                vscode.window.showErrorMessage(
                    "No Syntax errors found in the Java file but still failed to execute",
                );
                console.error({
                    rawError: axiosError,
                    status: axiosError.response.status,
                    body: axiosError.response.data,
                });
            }
        } else {
            vscode.window.showErrorMessage(
                `Failed to send Java file: ${axiosError.message}`,
            );
        }
        return false;
    }
}

async function hasDungeonIntrinsicsNextToSourceFile(
    document: vscode.TextDocument,
): Promise<boolean> {
    const currentDir = path.dirname(document.uri.fsPath);
    const markerFile = path.join(currentDir, "Dungeon", "Intrinsic.java");
    return pathExists(markerFile);
}

async function pathExists(targetPath: string): Promise<boolean> {
    try {
        await fs.access(targetPath);
        return true;
    } catch {
        return false;
    }
}

export async function stopBlocklyExecution() {
    const url = BLOCKLY_URL() + "/code?stop";
    try {
        await axios.post(
            url,
            {},
            {headers: {"Content-Type": "text/plain"}},
        );
        await axios.post(
            BLOCKLY_URL() + "/reset",
            {},
            {headers: {"Content-Type": "text/plain"}},
        ); // reset before any input
        showMessageWithTimeout("Blockly execution stopped");
    } catch (error: unknown) {
        if (axios.isAxiosError(error)) {
            vscode.window.showErrorMessage(
                `Failed to stop Java execution: ${error.message}`,
            );
            return;
        }

        vscode.window.showErrorMessage(
            `Failed to stop Java execution: ${toErrorMessage(error)}`,
        );
    }
}

function toErrorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

function displayErrorsInEditor(
    errorMessage: string,
    document: vscode.TextDocument,
    completeProgramMode: boolean,
): number {
    const diagnostics: vscode.Diagnostic[] = [];

    console.log(
        "=== displayErrorsInEditor called ===",
        "completeProgramMode:",
        completeProgramMode,
    );
    console.log("Raw error message:", errorMessage);

    // Strip RuntimeException wrapper if present
    // e.g., "ERROR: Exception executing code: java.lang.RuntimeException: <actual error>"
    let cleanedMessage = errorMessage;
    const runtimeExceptionMatch = errorMessage.match(
        /java\.lang\.RuntimeException:\s*(.*)/s,
    );
    if (runtimeExceptionMatch) {
        cleanedMessage = runtimeExceptionMatch[1];
        console.log(
            "Stripped RuntimeException, cleaned message:",
            cleanedMessage,
        );
    }

    // Parse Java compilation errors
    // Format is typically: "filename:line:column: error: message"
    const errorLines = cleanedMessage.split("\n");
    console.log(
        "Split into",
        errorLines.length,
        "lines:",
        errorLines.map((l, i) => `[${i}]: ${JSON.stringify(l)}`),
    );

    const errorRegex = /^(SEVERE|WARNING):\s*(.*):(\d+):(\d+):\s*(.+)$/;
    // Try to determine a more specific range for the error
    // For example, if the error message mentions a specific symbol
    const symbolRegex = /[sS]ymbol:\s+([mM]ethod|[vV]ariable)\s+([^\s(]+)/;
    const offsetRegex = /^(\s*)\^/; // only spaces and one ^

    let currentLineNum: number = -1;
    const currentError = {
        message: "",
        symbol: {
            name: "",
            type: "",
        },
        errorOffset: -1,
    };

    for (let i = 0; i < errorLines.length; i++) {
        const line = errorLines[i];
        const trimmedLine = line.trim();
        const errorMatch = errorRegex.exec(trimmedLine);
        const offsetMatch = offsetRegex.exec(line);
        const symbolMatch = symbolRegex.exec(trimmedLine);

        console.log(
            `Line ${i}: "${line}" | trimmed: "${trimmedLine}" | matches error: ${!!errorMatch} | matches offset: ${!!offsetMatch} | matches symbol: ${!!symbolMatch}`,
        );

        if (errorMatch) {
            console.log("Found error match:", errorMatch);
            // If we found a new error, process the previous one first
            if (currentError.message) {
                console.log("Adding previous error:", currentError);
                addDiagnostic(
                    diagnostics,
                    currentLineNum,
                    currentError,
                    document,
                );
            }

            // Parse new error
            const parsedLine = parseInt(errorMatch[3], 10);
            const parsedColumn = errorMatch[4]
                ? Math.max(parseInt(errorMatch[4], 10) - 1, 0)
                : -1;

            currentLineNum = completeProgramMode
                ? parsedLine - 1
                : parsedLine - wrapperOffset;
            currentLineNum = Math.max(
                0,
                Math.min(currentLineNum, document.lineCount - 1),
            ); // Clamp to valid range

            currentError.message = errorMatch[5];
            currentError.symbol.name = "";
            currentError.symbol.type = "";
            currentError.errorOffset = parsedColumn;

            console.log(
                `Parsed error: line=${parsedLine}, column=${parsedColumn}, mapped line=${currentLineNum}, message="${currentError.message}"`,
            );

            // Check if next line is the source code line
            if (i + 1 < errorLines.length) {
                const nextLine = errorLines[i + 1];
                const nextTrimmed = nextLine.trim();
                // If it doesn't match error/warning pattern and isn't a caret line, it's source
                if (
                    !errorRegex.test(nextTrimmed) &&
                    !offsetRegex.test(nextLine) &&
                    nextTrimmed.length > 0
                ) {
                    console.log("Found source line:", nextLine);
                    // This is the source line, we can use it for context
                    // Skip it in parsing by incrementing i
                    i++;
                    // Check if the line after source is the caret line
                    if (i + 1 < errorLines.length) {
                        const caretLine = errorLines[i + 1];
                        if (offsetRegex.test(caretLine)) {
                            const caretMatch = offsetRegex.exec(caretLine);
                            if (caretMatch) {
                                currentError.errorOffset = caretMatch[1].length;
                                console.log(
                                    "Found caret line, offset:",
                                    caretMatch[1].length,
                                );
                            }
                            i++;
                        }
                    }
                }
            }
        } else if (offsetMatch && currentError.message) {
            console.log("Found offset match:", offsetMatch);
            currentError.errorOffset = offsetMatch[1].length;
        } else if (symbolMatch && currentError.message) {
            console.log("Found symbol match:", symbolMatch);
            currentError.symbol.name = symbolMatch[2];
            currentError.symbol.type = symbolMatch[1];
        }
    }

    console.log("Final error object before adding:", currentError);
    // Add the last error if there is one
    if (currentError.message) {
        console.log("Adding final error:", currentError);
        addDiagnostic(diagnostics, currentLineNum, currentError, document);
    }

    console.log("Total diagnostics found:", diagnostics.length);
    // Set the diagnostics
    diagnosticCollection.set(document.uri, diagnostics);

    // Show error window with extracted diagnostics
    if (diagnostics.length > 0) {
        displayErrorWindow(diagnostics, document);
    }

    return diagnostics.length;
}

function displayErrorWindow(
    diagnostics: vscode.Diagnostic[],
    document: vscode.TextDocument,
): void {
    const errorCount = diagnostics.length;
    const firstError = diagnostics[0];

    // Build diagnostic summary
    let diagnosticsSummary = `Found ${errorCount} error${errorCount !== 1 ? "s" : ""}:\n\n`;
    diagnostics.slice(0, 5).forEach((diag, index) => {
        const line = diag.range.start.line + 1;
        const col = diag.range.start.character + 1;
        diagnosticsSummary += `${index + 1}. Line ${line}:${col} - ${diag.message}\n`;
    });
    if (errorCount > 5) {
        diagnosticsSummary += `\n... and ${errorCount - 5} more error${errorCount - 5 !== 1 ? "s" : ""}`;
    }

    console.log("=== Extracted Diagnostics ===");
    console.log(diagnosticsSummary);
    console.log("=============================");

    // Show error message with action buttons
    const openErrorAction = "Show First Error";
    const showAllErrorsAction = "Show All Errors";

    vscode.window
        .showErrorMessage(
            diagnosticsSummary,
            openErrorAction,
            showAllErrorsAction,
        )
        .then((selection) => {
            if (selection === openErrorAction) {
                navigateToError(document, firstError, 0);
            } else if (selection === showAllErrorsAction) {
                showAllErrorsInOutput(diagnostics, document);
            }
        });
}

function navigateToError(
    document: vscode.TextDocument,
    diagnostic: vscode.Diagnostic,
    errorIndex: number,
): void {
    const range = diagnostic.range;
    const position = range.start;

    // Open the document and navigate to error
    vscode.window.showTextDocument(document, {
        selection: range,
        preview: false,
    });

    // Optionally show the error in the output panel
    const outputChannel = vscode.window.createOutputChannel("Blockly Compiler");
    outputChannel.show(true);
    outputChannel.appendLine("=== Blockly Compilation Error ===");
    outputChannel.appendLine(
        `Error ${errorIndex + 1}: Line ${position.line + 1}, Column ${position.character + 1}`,
    );
    outputChannel.appendLine(`Message: ${diagnostic.message}`);
    outputChannel.appendLine("");

    console.log(
        `Navigating to error at line ${position.line + 1}, column ${position.character + 1}`,
    );
}

function showAllErrorsInOutput(
    diagnostics: vscode.Diagnostic[],
    document: vscode.TextDocument,
): void {
    const outputChannel = vscode.window.createOutputChannel(
        "Blockly Compiler Errors",
    );
    outputChannel.clear();
    outputChannel.show(true);

    outputChannel.appendLine(
        "╔════════════════════════════════════════════════╗",
    );
    outputChannel.appendLine(
        "║         BLOCKLY COMPILATION ERRORS             ║",
    );
    outputChannel.appendLine(
        "╚════════════════════════════════════════════════╝",
    );
    outputChannel.appendLine("");
    outputChannel.appendLine(`Total Errors: ${diagnostics.length}`);
    outputChannel.appendLine(`File: ${document.uri.fsPath}`);
    outputChannel.appendLine("");
    outputChannel.appendLine("─".repeat(50));

    diagnostics.forEach((diagnostic, index) => {
        const range = diagnostic.range;
        const line = range.start.line + 1;
        const col = range.start.character + 1;

        outputChannel.appendLine("");
        outputChannel.appendLine(
            `[${index + 1}/${diagnostics.length}] Error at Line ${line}, Column ${col}`,
        );
        outputChannel.appendLine(`Message: ${diagnostic.message}`);
        outputChannel.appendLine(
            `Severity: ${diagnostic.severity === vscode.DiagnosticSeverity.Error ? "ERROR" : diagnostic.severity === vscode.DiagnosticSeverity.Warning ? "WARNING" : "INFO"}`,
        );

        // Try to show the actual line content
        try {
            const lineContent = document.lineAt(line - 1);
            outputChannel.appendLine(`Source:  ${lineContent.text}`);

            // Show caret pointer
            const caretPos = " ".repeat(col - 1) + "^";
            outputChannel.appendLine(`         ${caretPos}`);
        } catch (e) {
            // Line might not exist
        }

        outputChannel.appendLine("─".repeat(50));
    });

    outputChannel.appendLine("");
    outputChannel.appendLine("Click on a line to navigate to that error.");
    outputChannel.appendLine(
        "Use the Problems panel (Ctrl+Shift+M) for more details.",
    );

    console.log("Opened All Errors view in output panel");
}

function addDiagnostic(
    diagnostics: vscode.Diagnostic[],
    lineNum: number,
    errorObj: {
        message: string;
        symbol: { name: string; type: string };
        errorOffset: number;
    },
    document: vscode.TextDocument,
) {
    console.log(
        `addDiagnostic: lineNum=${lineNum}, docLineCount=${document.lineCount}, errorOffset=${errorObj.errorOffset}, message="${errorObj.message}"`,
    );

    // Guard against negative line numbers
    if (lineNum < 0) {
        console.log("Skipping: lineNum < 0");
        return;
    }

    // Guard against line numbers that are too high
    if (lineNum >= document.lineCount) {
        console.log("Skipping: lineNum >= document.lineCount");
        return;
    }

    // Get the text of the line to determine the range
    const line = document.lineAt(Math.min(lineNum, document.lineCount - 1));
    const range = line.range;

    if (errorObj.symbol.name) {
        const symbolIndex = line.text.indexOf(errorObj.symbol.name);
        console.log(
            `Symbol lookup: "${errorObj.symbol.name}" found at index ${symbolIndex}`,
        );
        if (symbolIndex < 0) {
            diagnostics.push(
                new vscode.Diagnostic(
                    range,
                    errorObj.message,
                    vscode.DiagnosticSeverity.Error,
                ),
            );
            console.log("Added symbol error diagnostic (fallback to line)");
            return;
        }
        const start = new vscode.Position(lineNum, symbolIndex);
        const end = new vscode.Position(
            lineNum,
            symbolIndex + errorObj.symbol.name.length,
        );
        const preciseRange = new vscode.Range(start, end);
        diagnostics.push(
            new vscode.Diagnostic(
                preciseRange,
                errorObj.message.replace("Symbol", errorObj.symbol.type) +
                " ('" +
                errorObj.symbol.name +
                "')",
                vscode.DiagnosticSeverity.Error,
            ),
        );
        console.log("Added symbol-specific error diagnostic:", preciseRange);
        return;
    }

    // if no symbol was found, we check offset
    if (errorObj.errorOffset >= 0) {
        const start = new vscode.Position(lineNum, errorObj.errorOffset);
        const end = new vscode.Position(lineNum, errorObj.errorOffset + 1);
        const preciseRange = new vscode.Range(start, end);
        diagnostics.push(
            new vscode.Diagnostic(
                preciseRange,
                errorObj.message,
                vscode.DiagnosticSeverity.Error,
            ),
        );
        console.log(
            `Added offset-based error diagnostic at column ${errorObj.errorOffset}:`,
            preciseRange,
        );
        return;
    }

    // If we couldn't find a specific range, use the whole line
    diagnostics.push(
        new vscode.Diagnostic(
            range,
            errorObj.message,
            vscode.DiagnosticSeverity.Error,
        ),
    );
    console.log("Added fallback error diagnostic (whole line):", range);
}
