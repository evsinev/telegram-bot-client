package com.payneteasy.telegram.bot.client.model;

import lombok.Data;

/**
 * This object represents a bot command
 */
@Data
public class BotCommand {

    /**
     * Text of the command, 1-32 characters. Can contain only lowercase English letters, digits and underscores
     */
    private final String command;

    /**
     * Description of the command, 3-256 characters
     */
    private final String description;
}
