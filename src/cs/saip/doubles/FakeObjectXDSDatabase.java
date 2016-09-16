package cs.saip.doubles;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.w3c.dom.Document;

import cs.saip.storage.*;

/**
 * An fake object implementation of the XDS storage system, that is, it is a
 * lightweight in-memory implementation. (Which of course is not persistent!)
 * <p>
 * It also acts as a 'spy' as the last stored data can be accessed.
 * <p>
 * See http://en.wikipedia.org/wiki/Test_double
 * 
 * @author Henrik Baerbak Christensen, Aarhus University
 *
 */
public class FakeObjectXDSDatabase implements XDSBackend {
  
  private List<Pair> db = new ArrayList<Pair>();

  private Document lastStoredObservation;
  private MetaData lastMetaData;

  // spying on the behaviour
  private int countOfProvidedDocuments;
  
  public FakeObjectXDSDatabase() {
    countOfProvidedDocuments = 0;
  }

  class Pair { 
    public String uniqueId;
    public MetaData meta; 
    public Document doc; 
    public Pair(String uid, MetaData m, Document d) {
      uniqueId = uid;
      meta = m;
      doc = d;
    }
  }

  @Override
  public String provideAndRegisterDocument(MetaData metaData, Document observationAsHL7) {
    lastMetaData = metaData;
    lastStoredObservation = observationAsHL7;

    countOfProvidedDocuments++;
    String uniqueId = "uid-"+countOfProvidedDocuments; 
    // Store the observation in an internal 'database'
    db.add( new Pair(uniqueId, metaData, observationAsHL7));
    return uniqueId;
  }

  /** Spy / retrival interface to get the
   * last stored observation
   * @return last observation that has been stored
   */
  public Document getLastStoredObservation() {
    return lastStoredObservation;
  }

  /** Spy / retrival interface to get the
   * last stored metadata
   * @return last metadata that has been stored
   */
 public MetaData getLastMetaData() {
    return lastMetaData;
  }

  @Override
  public List<Document> retriveDocumentSet(String personID, LocalDateTime startTime,
      LocalDateTime endTime) {
    List<Document> thelist = new ArrayList<Document>();
    
    Instant instant; Date d;
    
    ZoneId cet = ZoneId.of("Europe/Paris");
    ZonedDateTime start2 = ZonedDateTime.of(startTime, cet);
    instant = start2.toInstant();
    d = Date.from(instant);
    long start = d.getTime();
    
    ZonedDateTime end2 = ZonedDateTime.of(endTime, cet);
    instant = end2.toInstant();
    d = Date.from(instant);
    long end = d.getTime();

    /* Java 8 version, probably not optimal */
    db.stream().
      filter(entry -> {
        MetaData md = entry.meta;
        return (start <= md.getTimestamp() && 
            md.getTimestamp() <= end &&
            personID.equals( md.getPersonID() ));
      }).
      forEach(entry -> thelist.add(entry.doc));
    
    /* Java 7 version of the code :)
    for ( Pair entry : db ) {
      MetaData md = entry.meta;
      if ( start <= md.getTimestamp() && 
          md.getTimestamp() <= end &&
          personID.equals( md.getPersonID() )) {
        thelist.add(entry.doc);
      }
    }
    */
    return thelist;
  }

  @Override
  public Document retriveDocument(String uniqueId) {
    Optional<Pair> foundPairInDB;
    foundPairInDB = findOptionalInDBWithUniqueId(uniqueId);
    Document doc = foundPairInDB.isPresent() ? foundPairInDB.get().doc : null;
    return doc;
  }

  private Optional<Pair> findOptionalInDBWithUniqueId(String uniqueId) {
    Optional<Pair> foundPairInDB;
    foundPairInDB = db.stream().
        filter(entry -> entry.uniqueId.equals(uniqueId)).
            findFirst();
    return foundPairInDB;
  }

  @Override
  public boolean correctDocument(String uniqueId, Operation operation, Document doc) {
    Optional<Pair> foundPairInDB;
    foundPairInDB = findOptionalInDBWithUniqueId(uniqueId);
    if (operation == Operation.UPDATE) {
      if (!foundPairInDB.isPresent()) { return false; }
      Pair entry = foundPairInDB.get();
      entry.doc = doc;
    } else if (operation == Operation.DELETE) {
      if (!foundPairInDB.isPresent()) { return false; }
      List<Pair> oneLessList = db.stream().
          filter(entry -> !entry.uniqueId.equals(uniqueId)).
          collect(Collectors.toList());
      db = oneLessList;
    }
    return true;
  }
}
