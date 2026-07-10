package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostReportResponseDto {

    private String message = "report_success";
    private Long postId;
    private int reportCount;
    private Long writerUserId;
    private int writerReceivedReportCount;

    public PostReportResponseDto(
            Long postId,
            int reportCount,
            Long writerUserId,
            int writerReceivedReportCount
    ) {
        this.postId = postId;
        this.reportCount = reportCount;
        this.writerUserId = writerUserId;
        this.writerReceivedReportCount = writerReceivedReportCount;
    }
}