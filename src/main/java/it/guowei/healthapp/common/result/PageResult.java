package it.guowei.healthapp.common.result;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 企业级分页结果封装
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer totalPages;

    public PageResult() {}

    public PageResult(List<T> records, Long total, Integer page, Integer size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) total / size);
    }

    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer size) {
        return new PageResult<>(records, total, page, size);
    }
}
