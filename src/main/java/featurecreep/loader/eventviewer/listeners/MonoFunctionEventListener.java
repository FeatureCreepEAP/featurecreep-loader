package featurecreep.loader.eventviewer.listeners;

import java.util.function.Function;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;
import featurecreep.loader.eventviewer.EventViewerEvent;
import featurecreep.loader.eventviewer.events.MonoAwareEvent;

public class MonoFunctionEventListener<A,T> implements EventListener<T> {

	
	String event_name;
	EventViewer eventvwr;
	Function<A,T> reference;
	
	
	public MonoFunctionEventListener(EventViewer eventvwr, String event_name,Function<A,T> reference) {
		this.event_name=event_name;
		this.eventvwr=eventvwr;
		this.reference=reference;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T invoke() throws IncompatibleEventListenerMethodReference {
		// TODO Auto-generated method stub
		EventViewerEvent<T> event = (EventViewerEvent<T>) eventvwr.getEvent(event_name);
		if(event instanceof MonoAwareEvent) {
			MonoAwareEvent<A,T> mono = (MonoAwareEvent<A, T>) event;
			return mono.invokeFunction(reference);
		}
		Object[] params = event.getParams();
		A param_0;
		try {
			param_0 = (A)params[0];
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IncompatibleEventListenerMethodReference();
		}

		if(params.length!=1) {
			throw new IncompatibleEventListenerMethodReference();
		}
		
		return reference.apply(param_0);
	}

	@Override
	public String getEventName() {
		// TODO Auto-generated method stub
		return event_name;
	}

}
