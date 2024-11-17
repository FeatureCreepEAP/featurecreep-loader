package featurecreep.loader.eventviewer.listeners;

import java.util.function.Supplier;

import featurecreep.loader.eventviewer.EventListener;
import featurecreep.loader.eventviewer.EventViewer;

public class SupplierEventListener<T> implements EventListener<T>{

	String event_name;
	EventViewer eventvwr;
	Supplier<T> reference;
	
	
	public SupplierEventListener(EventViewer eventvwr, String event_name, Supplier<T> reference) {
		this.event_name=event_name;
		this.eventvwr=eventvwr;
		this.reference=reference;
	}
	
	
	@Override
	public T invoke() throws IncompatibleEventListenerMethodReference {
		// TODO Auto-generated method stub
		return reference.get();
	}

	@Override
	public String getEventName() {
		// TODO Auto-generated method stub
		return event_name;
	}

}
