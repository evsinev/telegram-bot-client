package com.payneteasy.telegram.bot.client.messages;

import com.payneteasy.telegram.bot.client.model.Update;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class TelegramGetUpdatesResponse {
    // {"ok":true,"result":[]}
    boolean      ok;
    List<Update> result;
}
