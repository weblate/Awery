package com.mrboomdev.awery.util;

public class CallbackUtil {

	private CallbackUtil() {}

	public interface Callback1<T> {
		void run(T t);
	}
}