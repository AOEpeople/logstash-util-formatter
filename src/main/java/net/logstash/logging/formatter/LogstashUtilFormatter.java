/*
 * Copyright 2013 karl spies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.logstash.logging.formatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

/**
 *
 */
public class LogstashUtilFormatter extends Formatter {

    private static final JsonBuilderFactory BUILDER = Json.createBuilderFactory(null);
    private static String hostName;
    private static final String[] tags = System.getProperty(
            "net.logstash.logging.formatter.LogstashUtilFormatter.tags", "").split(",");

    static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";

    static {
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "unknown-host";
        }
    }

    @Override
    public final String format(final LogRecord record) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        final String dateString = dateFormat.format(new Date(record.getMillis()));
        final JsonArrayBuilder tagsBuilder = BUILDER.createArrayBuilder();
        for (final String tag : tags) {
            if (tag.length() > 0) tagsBuilder.add(tag);
        }

        return BUILDER
                .createObjectBuilder()
                .add("@timestamp", dateString)
                .add("@version", 1)
                .add("message", formatMessage(record))
                .add("logger_name", record.getLoggerName())
                .add("hostname", hostName)
                .add("level", record.getLevel().toString())
                .add("class", getValue(record.getSourceClassName()))
                .add("method", getValue(record.getSourceMethodName()))
                .add("throwable", encodeThrowable(record))
                .add("@tags", tagsBuilder.build())
                .build()
                .toString() + "\n";
    }

    @Override
    public synchronized String formatMessage(final LogRecord record) {
        String message = super.formatMessage(record);

        try {
            final Object parameters[] = record.getParameters();
            if (message == record.getMessage() && parameters != null && parameters.length > 0) {
                message = String.format(message, parameters);
            }
        } catch (Exception ex) {
        }

        return message;
    }

    /**
     * Enocde a possible throwable
     *
     * @param record the log record
     * @return objectBuilder
     */
    final JsonObjectBuilder encodeThrowable(final LogRecord record) {
        JsonObjectBuilder builder = BUILDER.createObjectBuilder();
        addThrowableInfo(record, builder);
        return builder;
    }

    /**
     * Format the stackstrace.
     *
     * @param record the logrecord which contains the stacktrace
     * @param builder the json object builder to append
     */
    final void addThrowableInfo(final LogRecord record, final JsonObjectBuilder builder) {
        if (record.getThrown() != null) {
            builder.add("line_number", getLineNumber(record));
            if (record.getSourceClassName() != null) {
                builder.add("exception_class",
                        record.getThrown().getClass().getName());
            }
            if (record.getThrown().getMessage() != null) {
                builder.add("exception_message",
                        record.getThrown().getMessage());
            }
            addStacktraceElements(record, builder);
        }
    }

    /**
     * Get the line number of the exception.
     *
     * @param record the logrecord
     * @return the line number
     */
    final int getLineNumber(final LogRecord record) {
        final int lineNumber;
        if (record.getThrown() != null) {
            lineNumber = getLineNumberFromStackTrace(
                    record.getThrown().getStackTrace());
        } else {
            lineNumber = 0;
        }
        return lineNumber;
    }

    /**
     * Gets line number from stack trace.
     * @param traces all stack trace elements
     * @return line number of the first stacktrace.
     */
    final int getLineNumberFromStackTrace(final StackTraceElement[] traces) {
        final int lineNumber;
        if (traces.length > 0 && traces[0] != null) {
            lineNumber = traces[0].getLineNumber();
        } else {
            lineNumber = 0;
        }
        return lineNumber;
    }

    private String getValue(final String value) {
        return (value != null) ? value : "null";
    }

    private void addStacktraceElements(final LogRecord record, final JsonObjectBuilder builder) {
        final StringWriter sw = new StringWriter();
        record.getThrown().printStackTrace(new PrintWriter(sw));
        builder.add("stacktrace", sw.toString());
    }
}
