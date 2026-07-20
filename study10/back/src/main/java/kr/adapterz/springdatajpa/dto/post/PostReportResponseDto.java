package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostReportResponseDto {

    private String message = "report_success";
    private Long postId;
    private int reportCount;

    public PostReportResponseDto(
            Long postId,
            int reportCount
    ) {
        this.postId = postId;
        this.reportCount = reportCount;
    }
}
