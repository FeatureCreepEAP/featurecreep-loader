package featurecreep.loader.eventviewer.events;

import java.util.function.Function;

import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.listeners.MonoFunctionEventListener;

public class MonoAwareEvent<A,T> extends BasicAwareEvent<T> {

	public A param;
	
	public MonoAwareEvent(EventViewer eventvwr, String event_name, A param) {
		super(eventvwr, event_name,param);
		this.param=param;
	}

	public MonoFunctionEventListener<A,T> registerMonoFunctionEventListener(Function<A,T> func) {
		MonoFunctionEventListener<A,T> ret = new MonoFunctionEventListener<A,T>(eventvwr,this.event_name,func);
		this.getEventViewer().registerListener(ret);
		return ret;
	}
	
	public A getParam() {
		return param;
	}
	
	public T invokeFunction(Function<A,T> func) {
		return func.apply(getParam());
	}
	
}
