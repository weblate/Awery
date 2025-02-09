package com.mrboomdev.awery.util.graphql;

import androidx.annotation.NonNull;

import java.util.Arrays;

public class GraphQLError {
	public Location[] locations;
	public String[] messages;
	public String message;
	public int status;

	public static class Location {
		public int line, column;
	}

	@NonNull
	@Override
	public String toString() {
		var builder = new StringBuilder()
				.append("{ ")
				.append(messages != null ? Arrays.toString(messages) : message)
				.append(", status: ")
				.append(status);

		if(locations != null) {
			builder.append(", locations: [ ");

			for(var location : locations) {
				builder.append("line: ")
						.append(location.line)
						.append("column: ")
						.append(location.column);
			}

			builder.append(" ]");
		}

		builder.append(" }");
		return builder.toString();
	}
}