package models;
import io.ebean.Finder;
import io.ebean.Model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Getter
@Setter
public class ScheduleTask extends Model {

  @Id private UUID taskUUID;

  @Column(nullable = false)
  private UUID scheduleUUID;

  @Column
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
  private Date completedTime;

  @Column
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
  private Date scheduledTime;

  public static final Finder<UUID, ScheduleTask> find =
      new Finder<UUID, ScheduleTask>(ScheduleTask.class) {};



  public static ScheduleTask fetchByTaskUUID(UUID taskUUID) {
    return find.query().where().eq("taskUUID", taskUUID).findOne();
  }

  public static List<ScheduleTask> getAll() {
    return find.query().findList();
  }

  public static ScheduleTask getLastTask(UUID scheduleUUID) {
    List<ScheduleTask> tasks =
        find.query()
            .where()
            .eq("scheduleUUID", scheduleUUID)
            .orderBy()
            .desc("scheduledTime")
            .findList();
    if (tasks.isEmpty()) {
      return null;
    }
    return tasks.get(0);
  }

  public static ScheduleTask getLastFullBackupTask(UUID scheduleUUID) {
    List<ScheduleTask> tasks =
        find.query()
            .where()
            .eq("scheduleUUID", scheduleUUID)
            .eq("isFullBackup", true)
            .orderBy()
            .desc("scheduledTime")
            .findList();
    if (tasks.isEmpty()) {
      return null;
    }
    return tasks.get(0);
  }

  public static Optional<ScheduleTask> getLastSuccessfulTask(UUID scheduleUUID) {
    return find
        .query()
        .where()
        .eq("scheduleUUID", scheduleUUID)
        .orderBy()
        .desc("scheduledTime")
        .findList()
        .stream()
        .findFirst();
  }

  public static List<ScheduleTask> getAllTasks(UUID scheduleUUID) {
    return find.query().where().eq("scheduleUUID", scheduleUUID).findList();
  }

  public void markCompleted() {
    this.completedTime = new Date();
    save();
  }
}
