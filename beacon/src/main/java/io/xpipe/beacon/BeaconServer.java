package io.xpipe.beacon;

import io.github.pixee.security.BoundedLineReader;
import io.xpipe.beacon.exchange.StopExchange;
import io.xpipe.core.process.OsType;
import io.xpipe.core.store.FileNames;
import io.xpipe.core.util.XPipeDaemonMode;
import io.xpipe.core.util.XPipeInstallation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Contains basic functionality to start, communicate, and stop a remote beacon server.
 */
public class BeaconServer {

    public static boolean isReachable() {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), BeaconConfig.getUsedPort()), 5000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> toProcessCommand(String toExec) {
        return OsType.getLocal().equals(OsType.WINDOWS) ? List.of("cmd", "/c", toExec) : List.of("sh", "-c", toExec);
    }

    public static Process tryStartCustom() throws Exception {
        var custom = BeaconConfig.getCustomDaemonCommand();
        if (custom != null) {
            var toExec =
                    custom + (BeaconConfig.getDaemonArguments() != null ? " " + BeaconConfig.getDaemonArguments() : "");
            var command = toProcessCommand(toExec);
            Process process = Runtime.getRuntime().exec(command.toArray(String[]::new));
            printDaemonOutput(process, command);
            return process;
        }
        return null;
    }

    public static Process start(String installationBase, XPipeDaemonMode mode) throws Exception {
        String command;
        if (!BeaconConfig.launchDaemonInDebugMode()) {
            command = XPipeInstallation.createExternalAsyncLaunchCommand(
                    installationBase, mode, BeaconConfig.getDaemonArguments());
        } else {
            command = XPipeInstallation.createExternalLaunchCommand(
                    getDaemonDebugExecutable(installationBase), BeaconConfig.getDaemonArguments(), mode);
        }

        var fullCommand = toProcessCommand(command);
        Process process = new ProcessBuilder(fullCommand).start();
        printDaemonOutput(process, fullCommand);
        return process;
    }

    private static void printDaemonOutput(Process proc, List<String> command) {
        boolean print = BeaconConfig.printDaemonOutput();
        if (print) {
            System.out.println("Starting daemon: " + command);
        }

        var out = new Thread(
                null,
                () -> {
                    try {
                        InputStreamReader isr = new InputStreamReader(proc.getInputStream());
                        BufferedReader br = new BufferedReader(isr);
                        String line;
                        while ((line = BoundedLineReader.readLine(br, 5_000_000)) != null) {
                            if (print) {
                                System.out.println("[xpiped] " + line);
                            }
                        }
                    } catch (Exception ioe) {
                        ioe.printStackTrace();
                    }
                },
                "daemon sysout");
        out.setDaemon(true);
        out.start();

        var err = new Thread(
                null,
                () -> {
                    try {
                        InputStreamReader isr = new InputStreamReader(proc.getErrorStream());
                        BufferedReader br = new BufferedReader(isr);
                        String line;
                        while ((line = BoundedLineReader.readLine(br, 5_000_000)) != null) {
                            if (print) {
                                System.err.println("[xpiped] " + line);
                            }
                        }
                    } catch (Exception ioe) {
                        ioe.printStackTrace();
                    }
                },
                "daemon syserr");
        err.setDaemon(true);
        err.start();
    }

    public static boolean tryStop(BeaconClient client) throws Exception {
        client.sendRequest(StopExchange.Request.builder().build());
        StopExchange.Response res = client.receiveResponse();
        return res.isSuccess();
    }

    public static String getDaemonDebugExecutable(String installationBase) {
        var osType = OsType.getLocal();
        var debug = BeaconConfig.launchDaemonInDebugMode();
        if (!debug) {
            throw new IllegalStateException();
        } else {
            if (BeaconConfig.attachDebuggerToDaemon()) {
                return FileNames.join(installationBase, XPipeInstallation.getDaemonDebugAttachScriptPath(osType));
            } else {
                return FileNames.join(installationBase, XPipeInstallation.getDaemonDebugScriptPath(osType));
            }
        }
    }
}
