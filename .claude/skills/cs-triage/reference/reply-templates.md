# CS reply templates

One shape per outcome. CS forwards this text to customers, so it must read as plain English.

## Voice rules (from Kesavan's actual replies)

- Open by addressing the reporter — `Hi @Jaz,` / `@Urmila`.
- Lead with the **verdict in plain words**, then the mechanism. **Bold** the one fact that explains
  everything; use `backticks` for a SKU or version.
- Numbered steps for anything the customer must do — imperative, in order.
- End with **explicit questions back**, and always ask whether other users are hitting it.
- Say plainly when we **couldn't reproduce**, and on what we tested.
- **Never** include: raw log lines, `tag`/class names, file paths, ticket internals, or a
  root-cause guess dressed up as fact.
- Don't assume the symptom. If the report is vague, ask for the specific values and a screenshot.

---

## A — Expected behaviour

```
Hi @<reporter>,

<Plain-language verdict — what's actually happening.>

<The mechanism, with the key fact in bold. Explain why what they're seeing is the
designed behaviour, in customer-safe words.>

*What to have them do:*
1. <step>
2. <step>

Could you confirm <the one detail that would change this answer>? And have any other
users reported the same on the `<SKU>` or other models — trying to work out whether
this is isolated or a wider pattern.
```

## B — Account / data state

```
Hi @<reporter>,

I checked the logs for this account — <what the log actually shows, e.g. "there's no
scale registered to it", "the same scale is paired twice on one user slot">. That's
why <the symptom>.

*To get them sorted:*
1. <step>
2. <step>
3. <step>

Once that's done, <what they should expect to see>. If it still doesn't work after
that, send a fresh log and we'll dig further.
```

## C — Real defect (ticket raised)

```
Hi @<reporter>,

We went through the logs. They confirm <what is established>, but <state honestly
whether they point to a cause>. We tested <what we tested — SKU, OS, device> on our
side and <could / couldn't> reproduce it.

From the logs, here's the sequence we *think* happened — can you confirm with the customer?
• <step in the sequence>
• <step in the sequence>

Two specifics that would help narrow it down:
1. <precise question>
2. <precise question>

Also — have any other <platform> users reported similar issues on the `<SKU>` or any
other model? Trying to work out if this is isolated to them or a wider pattern.

We've logged a ticket to track this on our side — <MOB-XXXX link>.

Thanks!
```

## D — Not enough evidence / fresh log needed

```
Hi @<reporter>,

Thanks for the details. <What the log does establish — and what it can't, in one line.
If the log predates the failure, say so plainly.>

Could you have them:
1. Open the app and go through <the exact flow> until it fails.
2. Right after it fails, send us a fresh log.

That way we're looking at the actual failed attempt. <If there's a mismatch — e.g. the
device in the report doesn't match the log — raise it here and ask them to confirm the
phone and OS they're actually using.>
```

## E — Already fixed in a newer build

```
Hi @<reporter>,

That one's already fixed — it went out in `<version>`.

Could you ask them to update to `<version>` and confirm whether the issue is resolved?
If it is, it'd help a lot if they updated their App Store / Play Store review too.

If they're already on `<version>` and still seeing it, send a fresh log — that would be
a different issue and we'll investigate.
```

---

## Verify before sending

- Reply names no internal identifiers, and no log lines.
- Every claim traces to log evidence or code **at the customer's version**.
- Steps are in the order the customer performs them.
- The isolated-vs-pattern question is present.
- If we couldn't reproduce, that's stated along with what we tested on.
