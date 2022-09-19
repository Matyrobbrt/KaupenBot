/*
 * ReLauncher - https://github.com/MinecraftModDevelopment/ReLauncher
 * Copyright (C) 2016-2022 <MMD - MinecraftModDevelopment>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * Specifically version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 */
package com.matyrobbrt.kaupenbot.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscordLogbackAppender extends AppenderBase<ILoggingEvent> {

    public static final Logger LOG = LoggerFactory.getLogger("DiscordLogbackAppender");

    public static final String POST_URL = "https://discord.com/api/v9/webhooks/%s/%s";
    public static void setup(String webhookUrl) throws ClassCastException {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final DiscordLogbackAppender appender = new DiscordLogbackAppender();
        appender.setContext(context);

        final DiscordLogbackLayout layout = new DiscordLogbackLayout();
        layout.setContext(context);
        layout.start();
        appender.setLayout(layout);

        appender.login(webhookUrl);
        appender.start();

        final ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
    }

    /**
     * The Layout.
     */
    private Layout<ILoggingEvent> layout;

    private WebhookClient client;

    public void login(String webhookUrl) {
        this.client = new WebhookClientBuilder(webhookUrl)
                .setDaemon(true)
                .build();
    }

    /**
     * Sets the inner {@link Layout}, used for formatting the message to be sent.
     *
     * @param layoutIn The layout
     */
    public void setLayout(final Layout<ILoggingEvent> layoutIn) {
        this.layout = layoutIn;
    }

    @Override
    protected void append(final ILoggingEvent eventObject) {
        if (client == null) return;
        client.send(new WebhookMessageBuilder()
                .setContent(getMessageContent(eventObject))
                .setAllowedMentions(AllowedMentions.none())
                .build());
    }

    protected String getMessageContent(final ILoggingEvent event) {
        return layout != null ? layout.doLayout(event) : event.getFormattedMessage();
    }
}
