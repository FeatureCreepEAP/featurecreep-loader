package featurecreep.loader.eventviewer.events;

import java.util.function.BiFunction;

import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.listeners.BiFunctionEventListener;

public class BiAwareEvent<C,U,T> extends BasicAwareEvent<T> {

	public C param0;
	public U param1;
	
	public BiAwareEvent(EventViewer eventvwr, String event_name, C param0, U param1) {
		super(eventvwr, event_name,param0, param1);
		this.param0=param0;
		this.param1=param1;
	}

	public BiFunctionEventListener<C,U,T> registerBiFunctionEventListener(BiFunction<C,U,T> func) {
		BiFunctionEventListener<C,U,T>	ret = new BiFunctionEventListener<C,U,T>(eventvwr,this.event_name,func);
		this.getEventViewer().registerListener(ret);
		return ret;
	}
	
	public C getParam0() {
		return param0;
	}
	
	public U getParam1() {
		return param1;
	}
	
	
	public T invokeFunction(BiFunction<C,U,T> func) {
		return func.apply(getParam0(),getParam1());
	}
	
}
