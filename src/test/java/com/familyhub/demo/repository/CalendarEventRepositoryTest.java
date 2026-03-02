package com.familyhub.demo.repository;

import com.familyhub.demo.config.TestcontainersConfig;
import com.familyhub.demo.model.CalendarEvent;
import com.familyhub.demo.model.Family;
import com.familyhub.demo.model.FamilyColor;
import com.familyhub.demo.model.FamilyMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CalendarEventRepositoryTest {

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    private Family createAndSaveFamily(String username) {
        Family family = new Family();
        family.setName("Test Family");
        family.setUsername(username);
        family.setPasswordHash("encoded");
        family.setFamilyMembers(new ArrayList<>());
        return familyRepository.save(family);
    }

    private FamilyMember createAndSaveMember(Family family) {
        FamilyMember member = new FamilyMember();
        member.setFamily(family);
        member.setName("John");
        member.setColor(FamilyColor.CORAL);
        return familyMemberRepository.save(member);
    }

    private CalendarEvent createAndSaveEvent(Family family, FamilyMember member) {
        CalendarEvent event = new CalendarEvent();
        event.setTitle("Test Event");
        event.setStartTime(LocalTime.of(9, 0));
        event.setEndTime(LocalTime.of(10, 0));
        event.setDate(LocalDate.of(2026, 3, 15));
        event.setFamily(family);
        event.setMember(member);
        family.getCalendarEvents().add(event);
        member.getCalendarEvents().add(event);
        return calendarEventRepository.save(event);
    }

    @Test
    void findByFamily_returnsEvents() {
        Family family = createAndSaveFamily("eventfamily");
        FamilyMember member = createAndSaveMember(family);
        createAndSaveEvent(family, member);

        List<CalendarEvent> result = calendarEventRepository.findByFamily(family);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("Test Event");
    }

    @Test
    void findByFamilyAndId_match() {
        Family family = createAndSaveFamily("matchfamily");
        FamilyMember member = createAndSaveMember(family);
        CalendarEvent event = createAndSaveEvent(family, member);

        Optional<CalendarEvent> result = calendarEventRepository.findByFamilyAndId(family, event.getId());

        assertThat(result).isPresent();
    }

    @Test
    void findByFamilyAndId_noMatch_wrongFamily() {
        Family family1 = createAndSaveFamily("family_one");
        Family family2 = createAndSaveFamily("family_two");
        FamilyMember member = createAndSaveMember(family1);
        CalendarEvent event = createAndSaveEvent(family1, member);

        Optional<CalendarEvent> result = calendarEventRepository.findByFamilyAndId(family2, event.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByFamilyAndId_noMatch_wrongId() {
        Family family = createAndSaveFamily("wrongidfamily");
        FamilyMember member = createAndSaveMember(family);
        createAndSaveEvent(family, member);

        Optional<CalendarEvent> result = calendarEventRepository.findByFamilyAndId(family, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void cascadeDelete_whenFamilyDeleted() {
        Family family = createAndSaveFamily("cascade_family");
        FamilyMember member = createAndSaveMember(family);
        createAndSaveEvent(family, member);

        family.getFamilyMembers().add(member);
        familyRepository.save(family);

        assertThat(calendarEventRepository.findByFamily(family)).hasSize(1);

        familyRepository.delete(family);
        familyRepository.flush();

        assertThat(calendarEventRepository.findAll()).isEmpty();
        assertThat(familyMemberRepository.findAll()).isEmpty();
    }

    @Test
    void cascadeDelete_whenMemberDeleted() {
        Family family = createAndSaveFamily("member_cascade");
        FamilyMember member = createAndSaveMember(family);
        CalendarEvent event = createAndSaveEvent(family, member);

        family.getFamilyMembers().add(member);
        familyRepository.save(family);

        assertThat(calendarEventRepository.findByFamily(family)).hasSize(1);

        // Remove event from family's collection and member from family — orphanRemoval handles deletion
        family.getCalendarEvents().remove(event);
        family.getFamilyMembers().remove(member);
        familyRepository.saveAndFlush(family);

        assertThat(calendarEventRepository.findAll()).isEmpty();
        assertThat(familyMemberRepository.findAll()).isEmpty();
        assertThat(familyRepository.findAll()).hasSize(1);
    }
}
