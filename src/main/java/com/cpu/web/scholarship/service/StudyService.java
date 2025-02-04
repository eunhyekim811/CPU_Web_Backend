package com.cpu.web.scholarship.service;

import com.cpu.web.member.repository.MemberRepository;
import com.cpu.web.scholarship.dto.StudyDTO;
import com.cpu.web.scholarship.entity.Study;
import com.cpu.web.scholarship.entity.MemberStudy;
import com.cpu.web.scholarship.repository.StudyRepository;
import com.cpu.web.scholarship.repository.MemberStudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final StudyRepository studyRepository;
    private final MemberRepository memberRepository;
    private final MemberStudyRepository memberStudyRepository;

    public Study createStudy(StudyDTO studyDTO, Long memberId) {
        // MemberRepository에서 유저가 존재하는지 확인
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("존재하지 않는 유저입니다.");
        }

        String name = studyDTO.getStudyName();
        String description = studyDTO.getStudyDescription();
        String typeStr = studyDTO.getStudyType().toLowerCase().trim(); // 소문자로 변환 후 공백 제거
        int max = studyDTO.getMaxMembers();

        if (name == null || name.isEmpty() || name.isBlank()) {
            throw new IllegalArgumentException("이름이 유효하지 않습니다.");
        }

        if (description == null || description.isEmpty() || description.isBlank()) {
            throw new IllegalArgumentException("설명이 유효하지 않습니다.");
        }

        if (typeStr == null || typeStr.isEmpty()) {
            throw new IllegalArgumentException("타입이 유효하지 않습니다.");
        }

        // 유효한 타입인지 확인 후 변환
        Study.StudyType type;
        switch (typeStr) {
            case "study":
                type = Study.StudyType.study;
                break;
            case "session":
                type = Study.StudyType.session;
                break;
            case "project":
                type = Study.StudyType.project;
                break;
            default:
                throw new IllegalArgumentException("유효하지 않은 스터디 타입입니다: " + typeStr);
        }

        if (max <= 0) {
            throw new IllegalArgumentException("최대 인원이 유효하지 않습니다.");
        }

        // 스터디 생성
        Study study = studyDTO.toStudyEntity();
        study.setMemberId(memberId);
        study.setStudyType(type); // 변환된 Enum 값 사용
        Study savedStudy = studyRepository.save(study);

        // MemberStudy에 팀장 정보 추가
        MemberStudy memberStudy = new MemberStudy();
        memberStudy.setMember(memberRepository.findById(memberId).get());
        memberStudy.setStudy(savedStudy);
        memberStudy.setIsLeader(true);
        memberStudyRepository.save(memberStudy);

        return savedStudy;
    }


    public Page<StudyDTO> getAllStudies(int page, int size) {
        Page<Study> studies = studyRepository.findAll(PageRequest.of(page, size));
        return studies.map(StudyDTO::new);
    }

    public Optional<StudyDTO> getStudyById(Long id) {
        return studyRepository.findById(id).map(StudyDTO::new);
    }

    public StudyDTO updateStudy(Long id, StudyDTO studyDTO, Long memberId) {
        Optional<MemberStudy> memberStudyOpt = memberStudyRepository.findByStudy_StudyIdAndMember_MemberId(id, memberId);

        if (memberStudyOpt.isEmpty()) {
            throw new IllegalArgumentException("수정 권한이 없는 유저입니다: " + memberId);
        }

        MemberStudy memberStudy = memberStudyOpt.get();
        if (!memberStudy.getIsLeader()) {
            throw new IllegalArgumentException("팀장이 아니므로 수정 권한이 없습니다: " + memberId);
        }

        Study study = studyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid study ID: " + id));

        study.setStudyName(studyDTO.getStudyName());
        study.setStudyDescription(studyDTO.getStudyDescription());
        study.setMaxMembers(studyDTO.getMaxMembers());

        // **문제 해결: studyType을 소문자로 변환 후 Enum으로 매핑**
        String typeStr = studyDTO.getStudyType().toLowerCase().trim();
        switch (typeStr) {
            case "study":
                study.setStudyType(Study.StudyType.study);
                break;
            case "session":
                study.setStudyType(Study.StudyType.session);
                break;
            case "project":
                study.setStudyType(Study.StudyType.project);
                break;
            default:
                throw new IllegalArgumentException("유효하지 않은 스터디 타입입니다: " + studyDTO.getStudyType());
        }

        return new StudyDTO(studyRepository.save(study));
    }


    public void deleteStudy(Long id, Long memberId) {
        Optional<MemberStudy> memberStudyOpt = memberStudyRepository.findByStudy_StudyIdAndMember_MemberId(id, memberId);

        if (memberStudyOpt.isEmpty()) {
            throw new IllegalArgumentException("삭제 권한이 없는 유저입니다: " + memberId);
        }

        MemberStudy memberStudy = memberStudyOpt.get();
        if (!memberStudy.getIsLeader()) {
            throw new IllegalArgumentException("팀장이 아니므로 삭제 권한이 없습니다: " + memberId);
        }

        if (!studyRepository.existsById(id)) {
            throw new IllegalArgumentException("Invalid Study ID: " + id);
        }

        studyRepository.deleteById(id);
    }
}
