package com.payneteasy.telegram.bot.client.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TelegramMessage {
    boolean ok;
    Message result;
}
