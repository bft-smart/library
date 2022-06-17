package bftsmart.tests;

import controller.BenchmarkControllerBootstrapper;

import java.util.Arrays;
import java.util.Properties;

public class BenchmarkControllerStarter {

	public static void main(String[] args) throws Exception {
		Properties properties = new Properties();
		System.out.println(Arrays.toString(args));
		for (String arg : args) {
			String[] tokens = arg.split("=");
			if (tokens.length != 2)
				continue;
			String propertyName = tokens[0].trim();
			String value = tokens[1].trim();
			switch (propertyName) {
				case "master.listening.ip":
				case "master.listening.port.client":
				case "master.listening.port.server":
				case "master.clients":
				case "master.servers":
				case "master.rounds":
				case "global.clients.maxPerWorker":
				case "global.isWrite":
				case "global.data.size":
				case "pod.network.interface":
				case "global.working.directory":
				case "controller.benchmark.strategy":
					break;
				default:
					throw new IllegalArgumentException("Unknown property name: " + propertyName);
			}
			properties.setProperty(propertyName, value);
		}
		new BenchmarkControllerBootstrapper(properties);
	}
}
