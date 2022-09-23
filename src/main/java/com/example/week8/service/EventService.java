package com.example.week8.service;

import com.example.week8.domain.Event;
import com.example.week8.domain.EventMember;
import com.example.week8.domain.Member;
import com.example.week8.dto.request.EventRequestDto;
import com.example.week8.dto.request.InviteMemberDto;
import com.example.week8.dto.response.EventResponseDto;
import com.example.week8.dto.response.MemberResponseDto;
import com.example.week8.dto.response.ResponseDto;
import com.example.week8.repository.EventMemberRepository;
import com.example.week8.repository.EventRepository;
import com.example.week8.repository.MemberRepository;
import com.example.week8.security.TokenProvider;
import com.example.week8.time.Time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EventService {

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final TokenProvider tokenProvider;

    /**
     * 약속 생성
     */
    public ResponseDto<?> createEvent(EventRequestDto eventRequestDto,
                                      HttpServletRequest request) {

        if (null == request.getHeader("RefreshToken")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        // 엔티티 조회
        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN");
        }

        // 약속 생성
        Event event = Event.builder()
                .title(eventRequestDto.getTitle())
                .eventDateTime(stringToLocalDateTime(eventRequestDto.getEventDateTime()))
                .place(eventRequestDto.getPlace())
                .content(eventRequestDto.getContent())
                .point(eventRequestDto.getPoint())
                .build();
        eventRepository.save(event);

        // 약속 멤버 생성
        EventMember eventMember = EventMember.createEventMember(member, event);  // 생성 시에는 약속을 생성한 member만 존재
        eventMemberRepository.save(eventMember);

        // MemberResponseDto에 Member 담기
        List<MemberResponseDto> list = new ArrayList<>();
        MemberResponseDto memberResponseDto = convertToDto(member);
        list.add(memberResponseDto);

        return ResponseDto.success(
                EventResponseDto.builder()
                        .id(event.getId())
                        .memberList(list)
                        .title(event.getTitle())
                        .eventDateTime(event.getEventDateTime())
                        .place(event.getPlace())
                        .createdAt(event.getCreatedAt())
                        .lastTime(Time.convertLocaldatetimeToTime(event.getEventDateTime()))
                        .content(event.getContent())
                        .point(event.getPoint())
                        .build()
        );
    }

    /**
     * 약속 수정
     */
    public ResponseDto<?> updateEvent(Long eventId,
                                      @RequestBody EventRequestDto eventRequestDto,
                                      HttpServletRequest request) {

        if (null == request.getHeader("RefreshToken")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        // 엔티티 조회
        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN");
        }

        // 약속 호출
        Event event = isPresentEvent(eventId);
        if (null == event) {
            return ResponseDto.fail("ID_NOT_FOUND");
        }
        if (validateEventMember(event, member)) {
            return ResponseDto.fail("NO_EVENTMEMBER");
        }

        // 약속 수정
        event.updateEvent(eventRequestDto);

        // MemberResponseDto에 Member 담기
        List<MemberResponseDto> list = new ArrayList<>();
        MemberResponseDto memberResponseDto = convertToDto(member);
        list.add(memberResponseDto);

        return ResponseDto.success(
                EventResponseDto.builder()
                        .id(event.getId())
                        .memberList(list)
                        .title(event.getTitle())
                        .eventDateTime(event.getEventDateTime())
                        .place(event.getPlace())
                        .createdAt(event.getCreatedAt())
                        .lastTime(Time.convertLocaldatetimeToTime(event.getEventDateTime()))
                        .content(event.getContent())
                        .point(event.getPoint())
                        .build()
        );
    }

    /**
     * 약속 단건 조회
     */
    @Transactional(readOnly = true)
    public ResponseDto<?> getEvent(Long eventId) {

        Event event = isPresentEvent(eventId);
        if (null == event) {
            return ResponseDto.fail("NOT_FOUND");
        }

        // MemberResponseDto에 Member 담기
        List<EventMember> findEventMemberList = eventMemberRepository.findAllByEventId(eventId);
        List<MemberResponseDto> tempList = new ArrayList<>();
        for (EventMember eventMember : findEventMemberList) {
            Long memberId = eventMember.getMember().getId();
            MemberResponseDto memberResponseDto = convertToDto(isPresentMember(memberId));
            tempList.add(memberResponseDto);
        }

        return ResponseDto.success(
                EventResponseDto.builder()
                        .id(event.getId())
                        .memberList(tempList)
                        .title(event.getTitle())
                        .eventDateTime(event.getEventDateTime())
                        .place(event.getPlace())
                        .createdAt(event.getCreatedAt())
                        .lastTime(Time.convertLocaldatetimeToTime(event.getEventDateTime()))
                        .content(event.getContent())
                        .point(event.getPoint())
                        .build()
        );
    }

    /**
     * 약속 삭제
     */
    public ResponseDto<?> deleteEvent(Long eventId, HttpServletRequest request) {

        if (null == request.getHeader("RefreshToken")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        // 엔티티 조회
        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN");
        }

        Event event = isPresentEvent(eventId);
        if (null == event) {
            return ResponseDto.fail("ID_NOT_FOUND");
        }

        // 멤버 유효성 검사
        if (validateEventMember(event, member)) {
            return ResponseDto.fail("NO_EVENTMEMBER");
        }

        // 약속 삭제, 약속과 연관된 약속멤버도 함께 삭제됨
        eventRepository.deleteById(eventId);

        return ResponseDto.success("약속이 삭제되었습니다.");
    }

    /**
     * 약속 초대(약속멤버 추가)
     */
    public ResponseDto<?> inviteMember(Long eventId,
                                       InviteMemberDto inviteMemberDto,
                                       HttpServletRequest request) {

        if (null == request.getHeader("RefreshToken")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        // 닉네임에 해당하는(초대할) 멤버 호출
        Member member = isPresentMemberByNickname(inviteMemberDto.getNickname());
        if (null == member) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        // 약속 호출
        Event event = isPresentEvent(eventId);

        // 약속 멤버 생성
        EventMember tempEventMember = EventMember.createEventMember(member, event);
        eventMemberRepository.save(tempEventMember);

        // MemberResponseDto에 Member 담기
        List<EventMember> findEventMemberList = eventMemberRepository.findAllByEventId(eventId);
        List<MemberResponseDto> tempList = new ArrayList<>();
        for (EventMember eventMember : findEventMemberList) {
            Long memberId = eventMember.getMember().getId();
            MemberResponseDto memberResponseDto = convertToDto(isPresentMember(memberId));
            tempList.add(memberResponseDto);
        }

        return ResponseDto.success(
                EventResponseDto.builder()
                        .id(event.getId())
                        .memberList(tempList)
                        .title(event.getTitle())
                        .eventDateTime(event.getEventDateTime())
                        .place(event.getPlace())
                        .createdAt(event.getCreatedAt())
                        .lastTime(Time.convertLocaldatetimeToTime(event.getEventDateTime()))
                        .content(event.getContent())
                        .point(event.getPoint())
                        .build()
        );
    }

    /**
     * 약속 탈퇴
     */
    public ResponseDto<?> exitEvent(Long eventId, HttpServletRequest request) {

        if (null == request.getHeader("RefreshToken")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        if (null == request.getHeader("Authorization")) {
            return ResponseDto.fail("MEMBER_NOT_FOUND");
        }

        // 멤버 호출
        Member member = validateMember(request);
        if (null == member) {
            return ResponseDto.fail("INVALID_TOKEN");
        }

        // 약속 호출
        Event event = isPresentEvent(eventId);

        // 약속 멤버 호출
        EventMember eventMember = isPresentEventMember(event, member);
        if(eventMember == null) {
            log.info("약속에 참여하지 않은 회원입니다, line : 294");
            return ResponseDto.fail("약속에 참여하지 않은 회원입니다.");
        }

        // 약속 멤버 삭제
        eventMemberRepository.deleteById(eventMember.getId());

        return ResponseDto.success("약속에서 탈퇴했습니다.");
    }

    
    //== 추가 메서드 ==//

    /**
     * eventMember 유효성 검사
     */
    public boolean validateEventMember(Event event, Member member) {
        Optional<EventMember> findEventMember = eventMemberRepository.findByEventIdAndMemberId(event.getId(), member.getId());
        return findEventMember.isEmpty();
    }

    /**
     * eventMember 호출
     */
    public EventMember isPresentEventMember(Event event, Member member) {
        Optional<EventMember> optionalEventMember = eventMemberRepository.findByEventIdAndMemberId(event.getId(), member.getId());
        return optionalEventMember.orElse(null);
    }

    /**
     * 멤버 유효성 검사
     */
    public Member validateMember(HttpServletRequest request) {
        if (!tokenProvider.validateToken(request.getHeader("RefreshToken"))) {
            return null;
        }
        return tokenProvider.getMemberFromAuthentication();
    }

    /**
     * 입력값 형변환 String to LocalDateTime
     */
    public LocalDateTime stringToLocalDateTime(String dateStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

        return LocalDateTime.parse(dateStr, formatter);
    }

    /**
     * 약속 호출
     */
    @Transactional(readOnly = true)
    public Event isPresentEvent(Long id) {
        Optional<Event> optionalEvent = eventRepository.findById(id);
        return optionalEvent.orElse(null);
    }

    /**
     * 멤버 호출 byId
     */
    @Transactional(readOnly = true)
    public Member isPresentMember(Long id) {
        Optional<Member> optionalMember = memberRepository.findById(id);
        return optionalMember.orElse(null);
    }

    /**
     * 멤버 호출 byNickname
     */
    @Transactional(readOnly = true)
    public Member isPresentMemberByNickname(String nickname) {
        Optional<Member> optionalMember = memberRepository.findByNickname(nickname);
        return optionalMember.orElse(null);
    }

    /**
     * Member를 MemberResponseDto로 변환
     */
    public MemberResponseDto convertToDto(Member member) {
        return MemberResponseDto.builder()
                .id(member.getId())
                .phoneNumber(member.getPhoneNumber())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .credit(member.getCredit())
                .point(member.getPoint())
                .profileImageUrl(member.getProfileImageUrl())
                .build();
    }
}
