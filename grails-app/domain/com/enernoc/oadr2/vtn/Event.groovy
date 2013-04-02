package com.enernoc.oadr2.vtn


import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

import javax.xml.datatype.DatatypeConfigurationException
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.Duration
import javax.xml.datatype.XMLGregorianCalendar

import com.enernoc.open.oadr2.model.DateTime
import com.enernoc.open.oadr2.model.Dtstart
import com.enernoc.open.oadr2.model.DurationPropType
import com.enernoc.open.oadr2.model.DurationValue
import com.enernoc.open.oadr2.model.EiActivePeriod
import com.enernoc.open.oadr2.model.EiEvent
import com.enernoc.open.oadr2.model.EiEventSignal
import com.enernoc.open.oadr2.model.EiEventSignals
import com.enernoc.open.oadr2.model.EventDescriptor
import com.enernoc.open.oadr2.model.Interval
import com.enernoc.open.oadr2.model.Intervals
import com.enernoc.open.oadr2.model.MarketContext
import com.enernoc.open.oadr2.model.Properties
import com.enernoc.open.oadr2.model.EventDescriptor.EiMarketContext


/**
 * A wrapper class to use the Play specific binding to cast 
 * a form to an EiEvent as well as manage the event itself
 * 
 * @author Jeff LaJoie
 *
 */
class Event{

    static belongsTo = [marketContext: Program]
    String programName
    //	@Required(message = "Must enter an Event ID")
    String eventID
    //@Required(message = "Must enter a Priority")
    //@Min(message = "Priority must be greater than or equal to 0", value = 0)
    //@Valid
    long priority
    String status = "none"
    //@Required(message = "Must enter a Start Date")
    String startDate
    //@Required(message = ("Must enter a Start Time"))
    String startTime
    //@Required(message = ("Must enter an End Date"))
    String endDate
    //@Required(message = ("Must enter an End Time"))
    String endTime
    //@Required(message = ("Must enter the number of intervals"))
    //@Min(message = "Priority must be greater than or equal to one", value = 1)
    //@Valid
    long intervals = 1
    long modificationNumber = 0
    //@Required(message = ("Must select a program, if one is not available please create one."))
    //String marketContext
    String duration
    String start
    public Map<String, String> statusTypes
    EiEvent eiEvent
    //static EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("Events")
    //static EntityManager entityManager = entityManagerFactory.createEntityManager()

    static constraints = {
        eventID(blank: false)
        priority(blank: false, min: 0L)
        startDate(blank: false)
        startTime(blank: false)
        endDate(blank: false)
        endTime(blank: false)
        intervals(min: 1L)
        duration(nullable: true,
        validator: {val, obj ->
            obj.getMinutesDuration() >= 0L
        }
        )
        start(nullable: true)
        status(nullable: true)
        eiEvent(nullable: true)
        modificationNumber(nullable: true)
        programName(validator: {val, obj ->
            obj.isConflicting()
        })

    }
    /**
     * Default constructor for an Event to initialize and set the date format
     */
    public Event(){
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy")
        Date date = new Date()
        startDate = dateFormat.format(date)
        endDate = dateFormat.format(date)
    }

    /**
    * Modified constructor which sets the current EiEvent to an Event for
     * editing purposes
     * 
     * @param event - the EiEvent to be cast to an Event wrapper class
     */
    public Event(EiEvent event){
        eiEvent = event
        this.eventID = event.eventDescriptor.eventID
        this.priority = event.eventDescriptor.priority
        this.status = event.eventDescriptor.eventStatus.value()
        this.start = event.eiActivePeriod.properties.dtstart.dateTime.value.toString()
        this.duration = event.eiActivePeriod.properties.duration.duration.value
        setStartDateTime(this.start)
        setEndDateTime()
    }

    /**
     * Unwraps the fields of the Event form to an EiEvent object
     * 
     * @return the unwrapped EiEvent with certain fields from the form filled
     */
    public EiEvent toEiEvent(){
        duration = createXCalString(getMinutesDuration())
        this.start = createXMLTime(startDate, startTime)
        DatatypeFactory xmlDataTypeFac = null
        try {
            xmlDataTypeFac = DatatypeFactory.newInstance()
        } catch (DatatypeConfigurationException e1) {
            e1.printStackTrace()
        }
        final XMLGregorianCalendar startDttm = xmlDataTypeFac.newXMLGregorianCalendar(start).normalize()
        return new EiEvent()
            .withEventDescriptor(new EventDescriptor()
                .withEventID(eventID)
                .withPriority(priority)
                .withCreatedDateTime(new DateTime(startDttm))
                .withModificationNumber(0))
            .withEiActivePeriod(new EiActivePeriod()
                .withProperties(new Properties()
                    .withDtstart(new Dtstart(new DateTime(startDttm)))
                    .withDuration(new DurationPropType(new DurationValue(duration)))))
            .withEiEventSignals(new EiEventSignals()
                .withEiEventSignals(new EiEventSignal()
                    .withIntervals(new Intervals()
                        .withIntervals(new Interval()
                            .withDuration( new DurationPropType(new DurationValue(duration)))))))
    }

    /**
     * Takes in a string of a date and a string of time in specific format
     * and readies it for a string accepted by the XMLGregorianCalendar
     * 
     * @param date - "MM-dd-yyyy"
     * @param time - "h:mm" || "hh:mm"
     * @return a String accepted by the the XMLGregorianCalendar
     */
    private String createXMLTime(String date, String time){
        String year = date.substring(6, 10)
        String month = date.substring(0, 2)
        String day = date.substring(3, 5)
        int hour = 0
        String tempString = time
        if(time.charAt(1) == ':'){
            tempString = ("0" + time)
        }
        hour = Integer.parseInt(tempString.substring(0, 2))
        if(hour == 12){
            hour = 0
        }
        if(time.charAt(6) == 'P'){
            hour += 12
        }
        String hourString = hour + ""
        if(hour < 10){
            hourString = "0" + hour
        }
        String minute = tempString.substring(3, 5)
        return year + "-" + month + "-" + day + "T" + hourString + ":" + minute + ":00"
    }

    /**
     * Returns a DateTime from two string inputs of date and time
     * 
     * @param date - "MM-dd-yyyy"
     * @param time - "h:mm" || "hh:mm"
     * @return a DateTime object for the EiEvent object
     */
    public DateTime createDateTime(String date, String time){
        int startYear = Integer.parseInt(date.substring(6, 10))
        int startMonth = Integer.parseInt(date.substring(0, 2))
        int startDay = Integer.parseInt(date.substring(3, 5))
        int startHour = Integer.parseInt(time.substring(0, 2))
        if(startHour == 12){
            startHour = 0
        }
        if(time.charAt(6) == 'P'){
            startHour += 12
        }
        int startMinute = Integer.parseInt(time.substring(3, 5))
        DateTime dateTime = new DateTime()
        DatatypeFactory xmlDataTypeFac = null
        try {
            xmlDataTypeFac = DatatypeFactory.newInstance()
        } catch (DatatypeConfigurationException e1) {
            e1.printStackTrace()
        }
        XMLGregorianCalendar calendar = xmlDataTypeFac.newXMLGregorianCalendar()
        calendar.setYear(startYear)
        calendar.setMonth(startMonth)
        calendar.setDay(startDay)
        calendar.setHour(startHour)
        calendar.setMinute(startMinute)
        calendar.setSecond(0)
        dateTime.setValue(calendar)
        return dateTime
    }

    /**
     * Returns the duration of an event in minutes
     * 
     * @return duration of the event in minutes
     */
    def getMinutesDuration(){
        DateTime startDateTime = createDateTime(startDate, startTime)
        DateTime endDateTime = createDateTime(endDate, endTime)

        long milliseconds = endDateTime.value.toGregorianCalendar().getTimeInMillis() - startDateTime.value.toGregorianCalendar().getTimeInMillis()
        long minutes = milliseconds / 60000

        return minutes
    }

    /**
     * Sets the start date and start time based on the DateTime String
     * 
     * @param startString - DateTime String parsed to make separate date and time Strings
     */
    private void setStartDateTime(String startString){
        this.startDate = makeStartDate(startString)
        this.startTime = makeStartTime(startString)
    }

    /**
     * Returns a formatted start date String from the form String
     * 
     * @param startString - Start String from the form
     * @return a formatted start date String
     */
    public String makeStartDate(String startString){
        return this.startDate = startString.substring(5, 7) + "-" + startString.substring(8, 10) + "-" + startString.substring(0, 4)
    }

    /**
     * Converts the form String to a formatted start time String
     * 
     * @param startString - Start String from the form
     * @return a formatted start time String
     */
    public String makeStartTime(String startString){
        int startHours = Integer.parseInt(startString.substring(11, 13))
        String startSuffix = " AM"
        if(startHours >= 12){
            startSuffix = " PM"
            if(startHours > 12){
                startHours -= 12
            }
        }
        String startHoursString = ""
        if(startHours < 10){
            startHoursString = "0" + startHours
        }
        else{
            startHoursString = "" + startHours
        }
        startHoursString = startHoursString.replace("00", "12")
        return (startHoursString + ":" + startString.substring(14, 16) + startSuffix)
    }

    /**
     * Sets the end date based on the start date and start time
     * based upon the Duration
     * 
     */
    private void setEndDateTime(){
        DatatypeFactory xmlDataTypeFac = null
        try {
            xmlDataTypeFac = DatatypeFactory.newInstance()
        } catch (DatatypeConfigurationException e1) {
            e1.printStackTrace()
        }
        DateTime endDateTime = createDateTime(startDate, startTime)
        Duration duration = xmlDataTypeFac.newDuration(this.duration.toString())
        endDateTime.value.add(duration)
        String endString = endDateTime.value.toString()

        this.endDate = endString.substring(5, 7) + "-" + endString.substring(8, 10) + "-" + endString.substring(0, 4)
        int startHours = Integer.parseInt(endString.substring(11, 13))

        String startSuffix = " AM"

        if(startHours >= 12){
            startSuffix = " PM"
            if(startHours > 12){
                startHours -= 12
            }
        }
        String startHoursString = startHours + ""
        if(startHours == 0){
            startHoursString = "12"
        }
        this.endTime = (startHoursString + ":" + endString.substring(14, 16) + startSuffix)
    }

    // takes a long of minutes and creates it into an XCal string
    // only positive and does not account for seconds
    /**
     * Takes a long in minutes and converts it to an XMLGregorianCalendar String
     * 
     * @param minutes - The number of minutes required by the String
     * @return the String properly formatted for XMLGregorianCalendar with 0 values omitted
     */
    public String createXCalString(long minutes){
        int years = (int) (minutes / 525949)
        minutes -= years * 545949
        int months = (int) (minutes / 43829)
        minutes -= months * 43829
        int days = (int) (minutes / 1440)
        minutes -= days * 1440
        int hours = (int) (minutes / 60)
        minutes -= hours * 60
        String returnString = "P"
        if(years > 0){
            returnString += (years + "Y")
        }
        if(months > 0){
            returnString += (months + "M")
        }
        if(days > 0){
            returnString += (days + "D")
        }
        returnString += "T"
        if(hours > 0){
            returnString += (hours + "H")
        }
        if(minutes > 0){
            returnString += (minutes + "M")
        }
        return returnString
    }

    /**
     * Converts an XMLGregorianCalendar string to an integer in minutes
     * 
     * @param xCal - the XMLGregorianCalendar string to be parsed
     * @return the total number of minutes contained in the XMLGregorianCalendar string
     */
    public static int minutesFromXCal(String xCal){
        Pattern p = Pattern.compile("P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        Matcher m = p.matcher(xCal)
        int returnMinutes = 0
        m.find()
        if(m.group(1) != null){
            returnMinutes += Integer.parseInt(m.group(1)) * 525949
        }
        if(m.group(2) != null){
            returnMinutes += Integer.parseInt(m.group(2)) * 43829
        }
        if(m.group(3) != null){
            returnMinutes += Integer.parseInt(m.group(3)) * 1440
        }
        if(m.group(4) != null){
            returnMinutes += Integer.parseInt(m.group(4)) * 60
        }
        if(m.group(5) != null){
            returnMinutes += Integer.parseInt(m.group(5))
        }
        return returnMinutes
    }

    /**
     * Displays a duration in minutes for the Events display page
     * 
     * @param s - the Duration to be displayed
     * @return a duration in minutes cast as a String
     */
    public String displayReadableDuration(String s){
        return "" + minutesFromXCal(s)
    }

    /**
     * Displays a human readable start date and time for the display page
     * 
     * @param s - the DateTime string
     * @return a human readable start date and time
     */
    public String displayReadableStart(String s){
        String time = makeStartTime(s)
        time = time.replace("00:", "12:")
        return makeStartDate(s) + " @ " + time
    }



    /**
     * Validates the Date and Time fields and returns a String if all is no as expected
     * 
     * @param startDate - the start date field
     * @param endDate - the end date field
     * @param startTime - the start time field
     * @param endTime - the end time form field
     * @return a String containing the error, null if otherwise
     */
    public String validation(String startDate, String endDate, String startTime, String endTime){
        String dateRegEx = "^([0|1]\\d)-([0-3]\\d)-(\\d+)"
        Pattern datePattern = Pattern.compile(dateRegEx)
        Matcher dateMatcher = datePattern.matcher(startDate)
        if(!dateMatcher.find()){
            return "Invalid start date."
        }
        dateMatcher = datePattern.matcher(endDate)
        if(!dateMatcher.find()){
            return "Invalid end date."
        }

        String timeRegEx = "^(1[0-2]|0[1-9]):([0-5][0-9])(\\s)?(?i)(am|pm)"
        Pattern timePattern = Pattern.compile(timeRegEx)
        Matcher timeMatcher = timePattern.matcher(startTime)
        if(!timeMatcher.find()){
            return "Invalid start time."
        }
        timeMatcher = timePattern.matcher(endTime)
        if(!timeMatcher.find()){
            return "Invalid end time."
        }
        if(!startIsBeforeEnd(startDate, startTime, endDate, endTime)){
            return "End date and time needs to occur after start date and time."
        }
        if(isConflicting()){
            return "Event overlaps with another within the same market context."
        }

        return null
    }


    /*compares if two events are conflicting by using events as oppose to eiEvents
     * modified to fit a groovier framework
     * @author Yang Xiang
     * 
     */
    private boolean isConflicting() {
        def SameProgramList = Event.findAllWhere(programName: programName)
        def start = createDateTime(startDate, startTime).value.toGregorianCalendar().getTimeInMillis()
        def end = createDateTime(endDate, endTime).value.toGregorianCalendar().getTimeInMillis()
        boolean returnValue = true
        SameProgramList.each { e ->
            if (e.id != this.id) {
                if (e.status != "cancelled" || e.status != "completed") {
                    def eStart = createDateTime(e.startDate, e.startTime).value.toGregorianCalendar().getTimeInMillis()
                    def eEnd = createDateTime(e.endDate, e.endTime).value.toGregorianCalendar().getTimeInMillis()
                    if (start <= eEnd) {
                        if (start < eStart) {
                            if (end >= eStart) {
                                returnValue = false
                            }
                        } else {
                            returnValue = false
                        }
                    }

                }
            }
        }
        return returnValue
    }

    /**
     * Creates a Duration from an EiEvent
     * 
     * @param event - the EiEvent to pull the Duration from
     * @return a Duration pulled from the DurationValue in EiEvent
     */
    public static Duration getDuration(EiEvent event){
        DatatypeFactory df = null
        try {
            df = DatatypeFactory.newInstance()
        } catch (DatatypeConfigurationException e) {
            e.printStackTrace()
        }
        return df.newDuration(event.eiActivePeriod.properties.duration.duration.value)
    }

    /**
     * Creates an EiEvent with only mandatory fields filled in
     * 
     * @return an incomplete EiEvent which is still fully acceptable for conflict comparison
     */
    public EiEvent getQuasiEvent(){
        return new EiEvent()
            .withEventDescriptor(new EventDescriptor()
                .withEventID(eventID)
                .withEiMarketContext(new EiMarketContext()
                    .withMarketContext(new MarketContext()
                        .withValue(entityManager.find(Program.class, Long.parseLong(marketContext)).getProgramName()))))
            .withEiActivePeriod(new EiActivePeriod()
                .withProperties(new Properties()
                    .withDtstart(new Dtstart()
                        .withDateTime(createDateTime(this.startDate, this.startTime)))
                    .withDuration(new DurationPropType()
                        .withDuration(new DurationValue()
                            .withValue(createXCalString(getMinutesDuration()))))))
    }


}
