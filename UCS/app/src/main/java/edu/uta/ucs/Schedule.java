package edu.uta.ucs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is used to store schedules.
 */
public class Schedule {

    public static final String SCHEDULE_NAMES = "SCHEDULE_NAMES";
    public static final String SCHEDULE_SAVEFILE = "SCHEDULE_SAVEFILE";

    private String name;
    private int semesterNumber;
    private ArrayList<Section> selectedSections;

    Schedule(String name, int semesterNumber, ArrayList<Section> sectionArrayList){
        this.name = name;
        this.semesterNumber = semesterNumber;
        this.selectedSections = sectionArrayList;
    }

    Schedule(JSONObject scheduleJSON) throws JSONException {

        name = scheduleJSON.getString("ScheduleName");
        semesterNumber = scheduleJSON.getInt("ScheduleSemester");
        Log.i("Schedule Course", scheduleJSON.getString("ScheduleCourses"));

        JSONArray scheduleCoursesJSONArray = scheduleJSON.getJSONArray("ScheduleCourses");

        ArrayList<Course> semesterCourses = Course.buildCourseList(scheduleCoursesJSONArray);
        selectedSections = new ArrayList<>(semesterCourses.size());
        for (Course course : semesterCourses){
            selectedSections.addAll(course.getSectionList());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public ArrayList<Section> getSelectedSections() {
        return selectedSections;
    }

    public void setSelectedSections(ArrayList<Section> selectedSections) {
        this.selectedSections = selectedSections;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject result = new JSONObject();

        result.put("ScheduleName", name);
        result.put("ScheduleSemester", getSemesterNumber());

        ArrayList<JSONObject> selectedSectionsString = new ArrayList<>(selectedSections.size());

        for (Section section : selectedSections){
            selectedSectionsString.add(section.getSourceCourse().toJSON(section));
        }
        JSONArray selectedSectionsJSONArray = new JSONArray(selectedSectionsString);

        result.put("ScheduleCourses",selectedSectionsJSONArray);

        return result;
    }

    /**
     * Builds an ArrayList of Schedules based on a JSONArray
     *
     * @param jsonSchedules JSONArray of schedules
     * @return ArrayList<Schedules>
     * @throws JSONException
     */
    public static ArrayList<Schedule> buildScheduleList(JSONArray jsonSchedules) throws JSONException {

        ArrayList<Schedule> scheduleList = new ArrayList<>(jsonSchedules.length());

        for(int index = jsonSchedules.length(); index != 0;index--){
            JSONObject scheduleJSON;
            try {
                scheduleJSON = jsonSchedules.getJSONObject(index - 1);
            }
            catch (JSONException e){
                Log.i("New Schedule JSON", "JSON Construction failed. Attempting to construct JSON from String");
                String courseString = jsonSchedules.getString(index - 1);
                scheduleJSON = new JSONObject(courseString);
            }

            Log.i("New Schedule JSON", "Adding to ArrayList: " + scheduleJSON.toString());
            scheduleList.add(new Schedule(scheduleJSON));
        }
        Collections.reverse(scheduleList);

        return scheduleList;
    }

    /**
     * Saves all schedules in the list of schedules provided to a sharedPreferance file. This will overwrite all schedules currently in that file.
     *
     * @param context Context to save with. Usually will be the calling class followed by ".this"
     *                EX: MainActivity.this
     * @param schedulesToSave ArrayList of schedules
     */
    public static void saveSchedulesToFile(Context context, ArrayList<Schedule> schedulesToSave){

        SharedPreferences.Editor scheduleEditor;
        scheduleEditor = context.getSharedPreferences(Schedule.SCHEDULE_SAVEFILE, Context.MODE_PRIVATE).edit();
        scheduleEditor.clear();

        ArrayList<JSONObject> savedSchedules = new ArrayList<>(schedulesToSave.size());
        ArrayList<String> scheduleNames = new ArrayList<>(schedulesToSave.size());

        for (Schedule schedule : schedulesToSave){
            scheduleNames.add(schedule.getName());
            try {
                savedSchedules.add(schedule.toJSON());
                scheduleEditor.putString(Schedule.SCHEDULE_NAMES + "_" + schedule.getName(), schedule.toJSON().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        HashSet<String> scheduleNameSet = new HashSet<>(scheduleNames);
        scheduleEditor.putStringSet(Schedule.SCHEDULE_NAMES, scheduleNameSet);
        scheduleEditor.apply();

    }

    public static ArrayList<Schedule> loadSchedulesFromFile(Context context){

        SharedPreferences scheduleFile = context.getSharedPreferences(Schedule.SCHEDULE_SAVEFILE, Context.MODE_PRIVATE);

        Set<String> scheduleNames = scheduleFile.getStringSet(Schedule.SCHEDULE_NAMES, null);
        if (scheduleNames == null)
            return  new ArrayList<>();
        ArrayList<Schedule> scheduleArrayList = new ArrayList<>(scheduleNames.size());
        for (String string : scheduleNames){

            String scheduleName = Schedule.SCHEDULE_NAMES + "_" + string;
            Log.i("Load Schedules", "Schedule Name" + scheduleName);

            String scheduleString = scheduleFile.getString(scheduleName, null);
            Log.i("Load Schedules", "Schedule String" + scheduleString);

            try {
                JSONObject scheduleJSON = new JSONObject(scheduleString);
                Schedule schedule = new Schedule(scheduleJSON);
                Log.i("Load Schedules", "Schedule JSON" + schedule.toJSON().toString());
                scheduleArrayList.add(schedule);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

        return scheduleArrayList;
    }

    public void showDetailedView(Context context){
        try {
            Intent scheduleIntent = new Intent(context, DetailedSchedule.class);
            scheduleIntent.putExtra("Schedule Data", toJSON().toString());
            context.startActivity(scheduleIntent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<Section> scheduleGenerator( ArrayList<Course> courseArrayList, ArrayList<Section> sectionArrayList, ArrayList<Section> blockOutTimesList) throws NoSchedulesPossibleException{
        return scheduleGenerator(0, courseArrayList,sectionArrayList,blockOutTimesList);
    }

    public static ArrayList<Section> scheduleGenerator(int index, ArrayList<Course> courseArrayList, ArrayList<Section> sectionArrayList, ArrayList<Section> blockOutTimesList) throws NoSchedulesPossibleException{

        NoSchedulesPossibleException scheduleConflict = null; //new NoSchedulesPossibleException();
        Log.i("schedule Factory", "Loop Counter:" + ((Integer) index).toString());
        if (index == courseArrayList.size()){
            return sectionArrayList;
        }
        Course course = courseArrayList.get(index);
        ArrayList<Section> possibleSections = new ArrayList<>(course.getSectionList().size());
        possibleSections.addAll(course.getSectionList());
        Collections.shuffle(possibleSections);
        // Shuffle sectionArrayList
        checkPossibleSections:

        for (Section section : possibleSections){
            if(section.getStatus() != ClassStatus.OPEN)
                continue;
            for (Section sectionToCompare : sectionArrayList){
                if (section.conflictsWith(sectionToCompare)){
                    if(scheduleConflict != null)
                        scheduleConflict.addConflict(section, sectionToCompare);
                    else scheduleConflict = new NoSchedulesPossibleException(section, sectionToCompare);
                    Log.e("Schedule Conflict Error", "Conflict between " + section.getSourceCourse().getCourseName() + " " + section.getSourceCourse().getCourseNumber() + "-" + section.getSectionNumber() + " and "
                            + sectionToCompare.getSourceCourse().getCourseName() + " " + sectionToCompare.getSourceCourse().getCourseNumber() + "-" + sectionToCompare.getSectionNumber());
                    continue checkPossibleSections;
                }
            }
            for (Section sectionToCompare : blockOutTimesList){
                if (section.conflictsWith(sectionToCompare)){
                    if (section.conflictsWith(sectionToCompare))
                        if(scheduleConflict != null)
                            scheduleConflict.addConflict(section, sectionToCompare);
                        else scheduleConflict = new NoSchedulesPossibleException(section, sectionToCompare);
                    Log.e("Schedule Conflict Error", "Conflict between " + section.getSourceCourse().getCourseName() + " " + section.getSourceCourse().getCourseNumber() + "-" + section.getSectionNumber() + " and "
                            + sectionToCompare.getSourceCourse().getCourseNumber() + " " + sectionToCompare.getInstructors());
                    continue checkPossibleSections;
                }
            }
            Log.i("Adding Section to List", section.toJSON().toString());
            sectionArrayList.add(section);
            try{
                return scheduleGenerator(index + 1, courseArrayList, sectionArrayList, blockOutTimesList);
            } catch (NoSchedulesPossibleException exception){
                exception.printStackTrace();
                sectionArrayList.remove(index);
                scheduleConflict = new NoSchedulesPossibleException(exception);
            }

        }throw scheduleConflict;

    }

    public int getSemesterNumber() {
        return semesterNumber;
    }
}

class NoSchedulesPossibleException extends Exception {

    String message;

    public NoSchedulesPossibleException(NoSchedulesPossibleException innerError) {
        if(innerError != null)
            this.message = this.message + "\n" + innerError.message;
        else
            this.message = "";
    }

    public NoSchedulesPossibleException(Section sourceSection, Section conflictingSection){
        if(conflictingSection.getSourceCourse().getCourseDepartment().equalsIgnoreCase("BLOCKOUT")){
            this.message = "Conflict between " + sourceSection.getSourceCourse().getCourseDepartment() +  " " + sourceSection.getSourceCourse().getCourseNumber() + "-" + sourceSection.getSectionNumber()
                    + " and " + conflictingSection.getSourceCourse().getCourseDepartment() +  ": " + conflictingSection.getInstructors();
        }
        else
            this.message = "Conflict between " + sourceSection.getSourceCourse().getCourseDepartment() +  " " + sourceSection.getSourceCourse().getCourseNumber() + "-" + sourceSection.getSectionNumber()
                + " and " + conflictingSection.getSourceCourse().getCourseDepartment() +  " " + conflictingSection.getSourceCourse().getCourseNumber() + "-" + conflictingSection.getSectionNumber();
    }

    public void addConflict(Section sourceSection, Section conflictingSection){

        if(conflictingSection.getSourceCourse().getCourseDepartment().equalsIgnoreCase("BLOCKOUT")){
            this.message = this.message + "\n" +
                    "Conflict between " + sourceSection.getSourceCourse().getCourseDepartment() +  " " + sourceSection.getSourceCourse().getCourseNumber() + "-" + sourceSection.getSectionNumber()
                    + " and " + conflictingSection.getSourceCourse().getCourseDepartment() +  ": " + conflictingSection.getInstructors();
        }
        else
            this.message = this.message + "\n" +
                    "Conflict between " + sourceSection.getSourceCourse().getCourseDepartment() +  " " + sourceSection.getSourceCourse().getCourseNumber() + "-" + sourceSection.getSectionNumber()
                    + " and " + conflictingSection.getSourceCourse().getCourseDepartment() +  " " + conflictingSection.getSourceCourse().getCourseNumber() + "-" + conflictingSection.getSectionNumber();
    }

    public String printConflict(){
        Log.e("Cannot Generate", message);
        return message;
    }

}
