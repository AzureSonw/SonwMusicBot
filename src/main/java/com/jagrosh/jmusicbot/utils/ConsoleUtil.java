/*
 * Copyright 2026 Arif Banai (arif-banai)
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
package com.jagrosh.jmusicbot.utils;

import java.io.PrintStream;
import javax.swing.*;

import com.jagrosh.jmusicbot.gui.TextAreaOutputStream;


/**
 * Utility class for redirecting System.out and System.err to a GUI text area.
 * This allows early redirection during startup so that logs appear in the GUI console.
 *
 * @author Arif Banai (arif-banai)
 */
public class ConsoleUtil {
    private static PrintStream consoleStream;
    private static JTextArea sharedTextArea;
    
    /**
     * Redirects System.out and System.err to a GUI text area.
     * This can be called early in startup (before GUI is fully initialized)
     * to capture logs that occur during configuration loading.
     * 
     * @return The JTextArea that will receive the console output
     */
    public static JTextArea redirectSystemStreams() {
        if (sharedTextArea == null) {
            sharedTextArea = new JTextArea();
            sharedTextArea.setLineWrap(true);
            sharedTextArea.setWrapStyleWord(true);
            sharedTextArea.setEditable(false);
            consoleStream = new PrintStream(new TextAreaOutputStream(sharedTextArea));
            System.setOut(consoleStream);
            System.setErr(consoleStream);
        }
        return sharedTextArea;
    }
    
    /**
     * Gets the shared text area if redirection has been set up, or null otherwise.
     * 
     * @return The shared JTextArea, or null if redirection hasn't been initialized
     */
    public static JTextArea getSharedTextArea() {
        return sharedTextArea;
    }
}
