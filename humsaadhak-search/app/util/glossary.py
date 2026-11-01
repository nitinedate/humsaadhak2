"""Plain-English meanings of Indian school, NGO and donation terms, added to a card's text when the term appears.

The models are pretrained on very large multilingual text, which covers donors, NGOs and schools well, but local
abbreviations and scheme names ("ZP school", "KGBV", "Schedule VII") are rare there. Spelling out their meaning, only
for the terms a card actually uses, lets the models compare what is meant rather than an unfamiliar word.
"""
import re

TERMS: dict[str, str] = {
    # schools and education programmes
    "zp school": "a government primary school run by the zilla parishad (district council)",
    "zilla parishad": "the elected district council that runs government primary schools",
    "anganwadi": "a government centre for pre-school education, nutrition and health of children under six",
    "balwadi": "a pre-school (nursery) class for young children",
    "icds": "Integrated Child Development Services, the government programme for young children's nutrition and pre-school",
    "ashram shala": "a residential government school for tribal (Adivasi) children",
    "ashram school": "a residential government school for tribal (Adivasi) children",
    "kgbv": "Kasturba Gandhi Balika Vidyalaya, a residential school for girls from disadvantaged communities",
    "navodaya": "Jawahar Navodaya Vidyalaya, a free residential government school for talented rural children",
    "jnv": "Jawahar Navodaya Vidyalaya, a free residential government school for talented rural children",
    "vidyalaya": "school",
    "shala": "school",
    "shiksha": "education",
    "samagra shiksha": "the national programme that funds school education",
    "rte": "the Right to Education Act: free and compulsory schooling for children aged 6 to 14",
    "udise": "the national register of schools",
    "smc": "the school management committee of parents and teachers",
    "mid-day meal": "the free cooked lunch served to children in government schools",
    "midday meal": "the free cooked lunch served to children in government schools",
    "mdm": "the mid-day meal: free cooked lunch served to children in government schools",
    "pm poshan": "the national school meal programme (formerly the mid-day meal scheme)",
    "smart class": "a digital classroom with a projector or interactive screen",
    "smart classroom": "a digital classroom with a projector or interactive screen",
    "atal tinkering lab": "a science and technology (STEM) lab for school students",
    "dropout": "a child who left school before finishing it",
    "drop-out": "a child who left school before finishing it",
    # people who receive help
    "adivasi": "tribal communities, the indigenous peoples of India",
    "divyang": "persons with disabilities",
    "cwsn": "children with special needs (children with disabilities)",
    "bpl": "below the poverty line (poor households)",
    "ews": "economically weaker section (low-income families)",
    "scheduled caste": "historically disadvantaged communities recognised in the Constitution",
    "scheduled tribe": "tribal communities recognised in the Constitution",
    "obc": "Other Backward Classes, socially and educationally disadvantaged communities",
    "daily-wage": "workers paid by the day, with low and irregular income",
    "beneficiary": "the child, school or community that receives the help",
    "benefactor": "a donor who funds the help",
    "philanthropist": "a donor who funds social causes",
    # funding, law and NGOs
    "csr": "corporate social responsibility: the funding companies must spend on social causes",
    "schedule vii": "the list of activities companies may fund under CSR law, such as education, health and sanitation",
    "80g": "the certificate that makes a donation to a charity tax-deductible",
    "12a": "the income-tax registration of a charitable trust or NGO",
    "fcra": "the permission an NGO needs to receive foreign donations",
    "section 8": "a non-profit company",
    "shg": "a self-help group: women who save together and take small loans",
    "self-help group": "a group of women who save together and take small loans",
    "panchayat": "the elected village council",
    # health, water and sanitation
    "wash": "water, sanitation and hygiene",
    "ro plant": "a drinking-water purifier (reverse osmosis)",
    "menstrual hygiene": "sanitary pads and education so girls do not miss school during their periods",
    "sanitary pad": "a menstrual hygiene product for girls and women",
    "phc": "a primary health centre, the government rural clinic",
    "asha worker": "a village community health worker",
}

_PATTERNS = [(term, re.compile(r"(?<![a-z0-9])" + re.escape(term) + r"s?(?![a-z0-9])")) for term in sorted(TERMS, key=len, reverse=True)]


def notes_for(text: str, limit: int = 8) -> list[str]:
    """'term: meaning' for each glossary term in the text, longest terms first, each meaning once. A matched term's
    text is then blanked, so a shorter term inside it ("shala" in "ashram shala") is not explained again."""
    lower = text.lower()
    notes, meanings = [], set()
    for term, pattern in _PATTERNS:
        found = False
        for m in list(pattern.finditer(lower)):
            found = True
            lower = lower[:m.start()] + " " * (m.end() - m.start()) + lower[m.end():]
        if found and TERMS[term] not in meanings:
            notes.append(f"{term}: {TERMS[term]}")
            meanings.add(TERMS[term])
            if len(notes) == limit:
                break
    return notes
