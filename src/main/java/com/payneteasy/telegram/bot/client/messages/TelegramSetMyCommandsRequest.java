package com.payneteasy.telegram.bot.client.messages;

import com.payneteasy.telegram.bot.client.model.BotCommand;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TelegramSetMyCommandsRequest {

    private final List<BotCommand> commands;

}
