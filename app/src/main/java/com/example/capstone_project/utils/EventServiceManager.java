package com.example.capstone_project.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.capstone_project.FirebaseController.RegItFirebaseController;
import com.example.capstone_project.models.Attendee;
import com.example.capstone_project.models.Event;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

// Singleton
public class EventServiceManager {
    private String TAG = "EventServiceManager";
    private static EventServiceManager instance;
    private static List<Event> events;
    private static final RegItFirebaseController db = RegItFirebaseController.getInstance();

    private EventServiceManager() {
        // Load events from database here ig
        updateEvents();
    }

    private static void sortEvents() {
        events.sort(new EventComparator());
    }

    public static EventServiceManager getInstance() {
        if (instance == null) {
            instance = new EventServiceManager();
        }
        sortEvents();
        return instance;
    }

    public static void updateEvents() {
        events = db.getEventsfromDB();
        sortEvents();
    }

    public void createEvent(String name, String description, String venue, String startDate, String endDate, double ticketPrice, int audienceLimit) {
        EventBuilder builder = new EventBuilder();
        Event event = builder
                .setEventName(name)
                .setVenue(venue)
                .setStartDateTime(startDate)
                .setEndDateTime(endDate)
                .setAudienceLimit(audienceLimit)
                .setDescription(description)
                .setTicketPrice(ticketPrice)
                .build();
        events.add(event);

        RegItFirebaseController.getInstance().createNewEvent(event);

        sortEvents();
    }

    public CompletableFuture<Boolean> registerAttendee(String eventId, Attendee newAttendee) {
        return db.addNewAttendee(eventId, newAttendee);
    }

    public void unRegisterAttendee(String eventId, String attendeeId) {

        db.removeAttendeeFromEvent(eventId, attendeeId);
        db.removeEventFromUser(eventId, attendeeId);
    }

    public Event getEventFromId(String eventId) {
        for (Event e : events) {
            if (e.getEventId().equals(eventId)) {
                return e;
            }
        }
        return null;
    }


    public CompletableFuture<String> getAttendeeIDFromName(String eventId, String attendeeName) {
        CompletableFuture<String> future = new CompletableFuture<>();

        DatabaseReference eventReference = RegItFirebaseController.getInstance()
                .getRegItEventsListDB()
                .child(eventId)
                .child("attendees");

        eventReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot attendeeSnapshot : dataSnapshot.getChildren()) {
                        Attendee attendee = attendeeSnapshot.getValue(Attendee.class);
                        if (attendee != null && attendee.getUserAccountName().equals(attendeeName)) {
                            Log.d("Attendee Found", "Attendee found: " + attendee.getUserAccountName());
                            future.complete(attendeeSnapshot.getKey());
                            return;
                        }
                    }
                    Log.d("Attendee Not Found", "Attendee not found: " + attendeeName);
                    future.complete(null); // Or throw an exception if desired
                } else {
                    Log.d("Event Not Found", "Event not found: " + eventId);
                    future.completeExceptionally(new Exception("Event or attendees not found"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w("Attendee ID Retrieval", "Failed to retrieve attendee ID", databaseError.toException());
                future.completeExceptionally(databaseError.toException());
            }
        });

        return future;
    }

    public Event[] getEvents() {
        return events.toArray(new Event[0]);
    }

    public CompletableFuture<Boolean> verifyAttendee(String eventId, Attendee attendeeId) {
        return db.verifyAttendee(eventId, attendeeId);
    }

    public void deleteEvent(String eventId) {
        Event e = getEventFromId(eventId);
        if (e == null) {
            Log.d(TAG, "Delete Event Error: Event not in list!");
            return;
        }
        db.deleteEventFromDB(eventId);
        events.remove(e);
    }

    public Task<String[]> getAttendeeNames(String eventId) {
        DatabaseReference eventReference = RegItFirebaseController.getInstance().getRegItEventsListDB().child(eventId);

        return eventReference.get()
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        Event event = task.getResult().getValue(Event.class);
                        if (event != null) {
                            List<String> attendeeNames = new ArrayList<>();
                            for (Attendee attendee : event.getAttendees().values()) {
                                attendeeNames.add(attendee.getUserAccountName());
                            }
                            return Tasks.forResult(attendeeNames.toArray(new String[0]));
                        } else {
                            throw new RuntimeException("Event not found");
                        }
                    } else {
                        throw Objects.requireNonNull(task.getException());
                    }
                });
    }
}

