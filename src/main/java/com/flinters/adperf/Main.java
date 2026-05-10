package com.flinters.adperf;

import com.flinters.adperf.cli.CliArgs;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        System.exit(new CommandLine(new CliArgs()).execute(args));
    }
}
