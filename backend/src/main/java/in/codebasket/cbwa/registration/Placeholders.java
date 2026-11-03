package in.codebasket.cbwa.registration;

import in.codebasket.cbwa.messaging.dto.SendMessageRequest;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.ButtonVariable;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.HeaderVariable;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.Media;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.TemplateRef;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.TemplateVariables;
import in.codebasket.cbwa.messaging.dto.SendMessageRequest.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Replaces {name}, {mobile}, {passNo} in a message (text, captions, template variables). */
final class Placeholders {

    private Placeholders() {
    }

    static Map<String, String> of(String name, String mobile, String passNo) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("{name}", name == null ? "" : name);
        m.put("{mobile}", mobile == null ? "" : mobile);
        m.put("{passNo}", passNo == null ? "" : passNo);
        return m;
    }

    static String apply(String s, Map<String, String> values) {
        if (s == null) {
            return null;
        }
        String out = s;
        for (var e : values.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    static List<String> apply(List<String> list, Map<String, String> values) {
        return list == null ? null : list.stream().map(v -> apply(v, values)).toList();
    }

    /** A copy of the request addressed to {@code to} from {@code senderId}, with placeholders filled in. */
    static SendMessageRequest apply(SendMessageRequest r, Long senderId, String to, Map<String, String> v) {
        Text text = r.text() == null ? null : new Text(apply(r.text().body(), v), r.text().previewUrl());
        Media media = r.media() == null ? null : new Media(r.media().fileUid(), r.media().metaMediaId(), r.media().link(),
                apply(r.media().caption(), v), r.media().filename());
        TemplateRef t = r.template();
        if (t != null) {
            TemplateVariables tv = t.variables();
            if (tv != null) {
                HeaderVariable h = tv.header();
                if (h != null) {
                    h = new HeaderVariable(h.type(), apply(h.text(), v), h.fileUid(), h.metaMediaId(), h.link(), h.filename(),
                            h.latitude(), h.longitude(), h.name(), h.address());
                }
                List<ButtonVariable> buttons = tv.buttons() == null ? null : tv.buttons().stream()
                        .map(b -> new ButtonVariable(b.index(), b.subType(), apply(b.value(), v))).toList();
                tv = new TemplateVariables(apply(tv.body(), v), h, buttons);
            }
            Map<String, String> named = null;
            if (t.namedVariables() != null) {
                named = new LinkedHashMap<>();
                for (var e : t.namedVariables().entrySet()) {
                    named.put(e.getKey(), apply(e.getValue(), v));
                }
            }
            t = new TemplateRef(t.templateId(), t.name(), t.language(), tv, named);
        }
        return new SendMessageRequest(senderId, null, to, r.type(), null, null, text, media, r.location(), r.contacts(), null,
                r.interactive(), t, r.clientReference());
    }
}
