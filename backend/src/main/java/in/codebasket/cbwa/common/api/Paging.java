package in.codebasket.cbwa.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Builds safe Pageable objects from query params: page >= 0, 1 <= size <= 100, whitelisted sort fields. */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowedSort, Sort defaultSort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        Sort srt = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            if (allowedSort.contains(field)) {
                Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                        ? Sort.Direction.ASC : Sort.Direction.DESC;
                srt = Sort.by(dir, field);
            }
        }
        return PageRequest.of(p, s, srt);
    }

    public static Pageable of(Integer page, Integer size) {
        return of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "id"));
    }
}
