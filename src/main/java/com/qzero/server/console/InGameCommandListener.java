package com.qzero.server.console;

import com.qzero.server.config.GlobalConfigurationManager;
import com.qzero.server.config.authorize.AuthorizeConfigurationManager;
import com.qzero.server.runner.*;
import com.qzero.server.utils.SHA256Utils;
import com.qzero.server.utils.UUIDUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InGameCommandListener implements ServerOutputListener {

    private String listenerId;

    private String attachedServerName;

    private AuthorizeConfigurationManager authorizeConfigurationManager;

    private MinecraftServerContainer container;

    private ServerCommandExecutor executor;

    private Map<String, ServerCommandContext> contextMap = new HashMap<>();

    public InGameCommandListener(String serverName) {
        attachedServerName = serverName;
        listenerId = UUIDUtils.getRandomUUID();

        container = MinecraftServerContainerSession.getInstance().getCurrentContainer();
        executor = ServerCommandExecutor.getInstance();

        authorizeConfigurationManager = GlobalConfigurationManager.getInstance().getAuthorizeConfigurationManager();
    }

    @Override
    public String getListenerId() {
        return listenerId;
    }

    @Override
    public void receivedOutputLine(String serverName, String outputLine, OutputType outputType) {
        if (outputType == OutputType.TYPE_ERROR)
            return;

        if (outputLine.matches(".*<.*> #.*")) {
            //Start in game command thread
            Pattern pattern = Pattern.compile("<.*>");
            Matcher matcher = pattern.matcher(outputLine);

            matcher.find();
            String id = matcher.group();
            id = id.replace("<", "");
            id = id.replace(">", "");

            pattern = Pattern.compile(" #.*");
            matcher = pattern.matcher(outputLine);
            matcher.find();

            String command = matcher.group();
            command = command.replace(" #", "");

            if(command.startsWith("reset_context")){
                contextMap.remove(id);
                tellToPlayerInGame(id,"Operator context reset");
                return;
            }

            ServerCommandContext context = contextMap.get(id);
            if (context == null) {
                context = new ServerCommandContext();
                context.setCurrentServer(serverName);
                context.setOperatorId(id);
                contextMap.put(id, context);
            }

            String returnValue = executor.executeCommand(command, context);
            tellToPlayerInGame(id, returnValue);
        }
    }

    @Override
    public void receivedServerEvent(String serverName, ServerEvent event) {

    }

    private void tellToPlayerInGame(String playerName, String message) {
        if (container.getServerOperator(attachedServerName).getServerStatus() != MinecraftRunner.ServerStatus.RUNNING)
            throw new IllegalStateException("Server is not running");

        String[] lines = message.split("\n");
        for (String line : lines) {
            container.getServerOperator(attachedServerName).sendCommand(String.format("/tell %s %s", playerName, line));
        }
    }

}
