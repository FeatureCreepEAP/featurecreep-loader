package featurecreep.loader.eventviewer.listeners;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;

public class ObjectEventListener<T> implements EventListener<T>{

	String event_name;
	EventViewer eventvwr;
	T reference;
	
	
	public ObjectEventListener(EventViewer eventvwr, String event_name, T reference) {
		this.event_name=event_name;
		this.eventvwr=eventvwr;
		this.reference=reference;
	}
	
	
	@Override
	public T invoke() throws IncompatibleEventListenerMethodReference {
		// TODO Auto-generated method stub
		return reference;
	}

	@Override
	public String getEventName() {
		// TODO Auto-generated method stub
		return event_name;
	}

}
