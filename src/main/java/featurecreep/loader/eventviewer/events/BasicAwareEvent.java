package featurecreep.loader.eventviewer.events;

import featurecreep.loader.eventviewer.EventViewer;

public class BasicAwareEvent<T> extends BasicEvent<T> implements EventVwrAwareEvent<T> {

	public EventViewer eventvwr;

	public BasicAwareEvent(EventViewer eventvwr, String event_name, Object... params) {
		super(event_name, params);
		this.eventvwr = eventvwr;
	}

	@Override
	public EventViewer setEventViewer(EventViewer eventvwr) {
		// TODO Auto-generated method stub
		this.eventvwr = eventvwr;
		return eventvwr;
	}

	@Override
	public EventViewer getEventViewer() {
		// TODO Auto-generated method stub
		return eventvwr;
	}

}
