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

    try {
        const queryParams = new URLSearchParams();
        queryParams.set("sleep", String(SLEEP_AFTER_EACH_LINE()));

        if (options.waitForDebugger) {
            queryParams.set("waitForDebugger", "1");
        }

        const completeProgramMode = await resolveCompleteProgramMode(
            editor.document,
        );
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
): number {
    const diagnostics: vscode.Diagnostic[] = [];

    // Parse Java compilation errors
    // Format is typically: "filename:line: error: message"
    const errorLines = errorMessage.split("\n");

    const errorRegex = /UserScript\.java:(\d+): ([fF]ehler|[eE]rror): (.+)/;
    // Try to determine a more specific range for the error
    // For example, if the error message mentions a specific symbol
    const symbolRegex = /[sS]ymbol:\s+([mM]ethode|[vV]ariable)\s+([^\s(]+)/;
    const offsetRegex = /(\s*)\^/; // only spaces and one ^

    let currentLineNum: number = -1;
    const currentError = {
        message: "",
        symbol: {
            name: "",
            type: "",
        },
        errorOffset: -1,
    };

    for (const line of errorLines) {
        const errorMatch = errorRegex.exec(line);
        const offsetMatch = offsetRegex.exec(line);
        const symbolMatch = symbolRegex.exec(line);

        if (errorMatch) {
            // If we found a new error, we want to to see if we can find a symbol
            // if we find a new error, we want to process the previous one first even if we don't have a symbol
            if (currentError.message) {
                addDiagnostic(
                    diagnostics,
                    currentLineNum,
                    currentError,
                    document,
                );
            }

            // set to the new error
            currentError.message = errorMatch[3];
            currentError.symbol.name = "";
            currentError.symbol.type = "";
            currentError.errorOffset = -1;

            currentLineNum = parseInt(errorMatch[1], 10) - wrapperOffset;
            currentLineNum = Math.max(
                0,
                Math.min(currentLineNum, document.lineCount - 1),
            ); // Clamp to valid range
        }

        if (offsetMatch) {
            currentError.errorOffset = offsetMatch[1].length;
        }

        if (symbolMatch) {
            currentError.symbol.name = symbolMatch[2];
            currentError.symbol.type = symbolMatch[1];
        }
    }

    // Add the last error if there is one
    if (currentError.message)
        addDiagnostic(diagnostics, currentLineNum, currentError, document);

    // Set the diagnostics
    diagnosticCollection.set(document.uri, diagnostics);

    return diagnostics.length;
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
    // Guard against negative line numbers
    if (lineNum < 0) return;

    // Guard against line numbers that are too high
    if (lineNum >= document.lineCount) return;

    // Get the text of the line to determine the range
    const line = document.lineAt(Math.min(lineNum, document.lineCount - 1));
    const range = line.range;

    if (errorObj.symbol.name) {
        const symbolIndex = line.text.indexOf(errorObj.symbol.name);
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
}
