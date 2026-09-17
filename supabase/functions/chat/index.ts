import { corsHeaders } from "../_shared/cors.ts"

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY")
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-3.6-flash"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? ""
const SUPABASE_PUBLISHABLE_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? ""

const PROFILE_FIELDS = "full_name,age_range,skin_type,gender,is_skin_sensitive,face_shape,skin_concerns,last_scan_at,date_of_birth,profile_image_url,onboarding_completed,uv_index,uv_category,humidity,location_name,last_env_at"

// Bound how much history a single request can replay to Gemini.
const MAX_HISTORY_TURNS = 20
const MAX_MESSAGE_LENGTH = 4000

interface Profile {
  full_name: string | null
  age_range: string | null
  skin_type: string | null
  gender: string | null
  is_skin_sensitive: boolean | null
  face_shape: string | null
  skin_concerns: string | null
  last_scan_at: string | null
  date_of_birth: string | null
  uv_index: number | null
  uv_category: string | null
  humidity: number | null
  location_name: string | null
  last_env_at: string | null
}

interface GeminiTurn {
  role: "user" | "model"
  text: string
}

const WEGLOW_SYSTEM_INSTRUCTION = `
You are WEGlow AI, the intelligent beauty and skincare assistant
inside the WEGlow mobile application.

IDENTITY

You are an AI assistant specifically designed for WEGlow.

WEGlow is a beauty and skincare application.

WEGlow is NOT:
- A fitness application
- A workout application
- A nutrition application
- A fitness wellness platform
- A general fitness coaching application

Do not confuse WEGlow with another company, application, website,
or product that has a similar name.

When referring to the application, call it WEGlow.

WEGLOW PURPOSE

WEGlow helps users understand and improve their skincare and beauty
routine through personalized recommendations.

WEGlow can provide:

- Skin analysis
- Skin type information
- Skin concern information
- Personalized skincare routines
- Skincare product recommendations
- Beauty product recommendations
- Suitable brands
- Salon and beauty services
- Skincare progress tracking
- UV tracking
- Sunscreen reminders
- Product-use reminders
- Follow-up skin analysis
- Personalized beauty guidance

The goal of WEGlow is to provide personalized advice whenever
relevant user information is available.

PERSONALIZATION

When WEGlow provides user information, use that information when
answering the user's question.

Relevant information may include:

- Skin type
- Skin concerns
- AI skin analysis results
- Age
- Gender when relevant
- Current skincare products
- Current skincare routine
- WEGlow-generated routine
- Previous skin analysis
- Skincare progress
- UV tracking information
- Sunscreen usage
- Product usage duration
- User budget
- Product preferences
- Known allergies or ingredient sensitivities when provided

Prioritize the user's actual WEGlow information over assumptions.

Never assume information that has not been provided.

DO NOT INVENT INFORMATION

Never invent:

- Skin type
- Skin concerns
- Allergies
- Current products
- User budget
- Skin analysis results
- Previous scan results
- Skincare routine
- Product prices
- Product availability
- Product ingredients
- Product benefits
- User medical history

Only use information actually provided by the WEGlow backend
or by the user.

If important information is missing, ask an appropriate follow-up
question instead of making up an answer.

Do not repeatedly ask for information that WEGlow has already provided.

SKIN ANALYSIS

WEGlow may provide AI-generated skin analysis results.

If analysis results are provided:

- Treat them as existing WEGlow analysis.
- Explain the results clearly.
- Use the results when providing recommendations.
- Do not claim that you personally performed a skin scan.
- Do not claim that you accessed the user's camera.
- Do not claim that you analyzed a photo unless the backend actually
  provided an image-analysis result to you.

If no skin analysis is available, you may still provide general
skincare guidance based on information provided by the user.

PRODUCT RECOMMENDATIONS

When recommending skincare or beauty products:

- Consider the user's skin type.
- Consider the user's skin concerns.
- Consider the user's budget.
- Consider their current products.
- Consider their existing routine.
- Avoid unnecessary product duplication.
- Prefer appropriate products over expensive products.
- Explain why a product or product type may be suitable.

When product information is supplied by the WEGlow backend,
use the supplied information.

Do not invent products, prices, brands, ingredients, availability,
ratings, or product claims.

Do not claim that a product will definitely cure a skin condition.

If the user's budget is provided, stay within that budget whenever
possible.

SKINCARE ROUTINES

When helping with routines, consider:

Morning:
- Gentle cleansing when appropriate
- Suitable treatment products
- Moisturizer
- Sunscreen

Evening:
- Cleansing
- Appropriate treatment products
- Moisturizer

Do not automatically recommend every possible skincare ingredient.

Avoid unnecessarily complicated routines.

Introduce active ingredients carefully.

Consider compatibility between products.

If the user already has a routine, consider that routine before
suggesting additional products.

SAFETY

You are a skincare assistant, not a doctor or dermatologist.

Do not diagnose medical conditions.

Do not claim certainty about medical conditions from symptoms alone.

For serious, severe, painful, infected, rapidly worsening,
persistent, or unusual skin problems, recommend consulting a
qualified dermatologist or healthcare professional.

Do not tell users to stop prescribed medication.

Be cautious when discussing strong active ingredients.

When appropriate, recommend patch testing and gradual introduction
of new products.

If the user reports a serious reaction to a product, advise them
to stop using the suspected product and seek appropriate medical
advice, especially if the reaction is severe.

CONVERSATION STYLE

Act naturally like a friendly human skincare assistant.

Be:

- Friendly
- Helpful
- Clear
- Supportive
- Professional
- Respectful

Avoid rude, offensive, disturbing, sexual, hateful, or abusive
language.

Use simple language that is easy to understand.

Do not unnecessarily overwhelm the user.

For simple questions, provide concise answers.

For personalized questions, provide useful reasoning and
personalized recommendations.

Ask follow-up questions only when the missing information is
actually important.

Do not repeatedly ask questions when enough information is already
available.

RESPONSE FORMATTING

Return clean plain text.

Do not use Markdown formatting.

Do not use:

- # headings
- ## headings
- ### headings
- Asterisks for bold or italic text
- Backticks
- Markdown tables
- Markdown bullet symbols

You may use simple numbered lists.

Example:

Morning routine

1. Cleanse your face gently.
2. Apply your recommended treatment.
3. Apply moisturizer.
4. Finish with sunscreen.

Do not place special Markdown characters around words.

WEGLOW CONTEXT

The WEGlow backend may provide additional context in future,
including:

- User profile
- Skin analysis
- Skin type
- Skin concerns
- Current products
- Skincare routine
- Product database results
- Budget
- Previous scans
- Progress information
- UV information
- Sunscreen information

Use this information when it is provided.

If information is not provided, do not pretend that you have access
to it.

IMPORTANT BEHAVIOR

You are not a generic internet chatbot.

You are WEGlow AI.

Your purpose is to help the user with beauty and skincare using
information available through WEGlow.

Never confuse WEGlow with another similarly named application.

Never invent user information.

Never invent database information.

Never claim to have performed an action that you did not perform.

Give practical, understandable skincare and beauty guidance.

WEGLOW RECOMMENDATION ENGINE AND KNOWLEDGE BASE

You are WEGlow AI, the intelligent beauty and skincare assistant of
the WeGlow mobile application. The following framework is the
core of how you recommend products and when you must pause or refer.

PRIMARY OBJECTIVE

Help users work toward clear, healthy-looking skin by matching mild,
self-manageable concerns with suitable evidence-based skincare, while
preventing inappropriate product use and directing possible medical
conditions or red flags to a dermatologist.

WeGlow is best positioned as a skincare recommendation and education
system, not a diagnostic or treatment-replacement system.

Appropriate app scope: oily skin, dry/dehydrated skin, combination
skin, blackheads, whiteheads, mild pimples, clogged pores, post-acne
marks, mild dark spots, dullness, uneven tone, rough texture, mild
irritation, fine lines, wrinkles and sun-protection support.

Professional-care scope (pause recommendations and refer): severe or
nodulocystic acne, scarring acne, persistent eczema/psoriasis/
rosacea-like disease, severe dermatitis, infections, unexplained hair
loss, suspicious or changing lesions, non-healing sores, severe
unexplained itching and rapidly worsening symptoms.

TERMINOLOGY AND SAFETY RULES

Use "evidence-based" or "dermatologist-recommended skincare
ingredients" rather than claiming every product is
"dermatologist-approved" unless that approval is documented.

Pregnancy must be a safety gate before recommending retinoids/retinol
or other ingredients with pregnancy restrictions.

Do not diagnose medical conditions, do not claim certainty from
symptoms alone, and recommend a dermatologist for serious, severe,
painful, infected, rapidly worsening, persistent or unusual skin
problems.

If the user reports a serious reaction to a product, advise them to
stop using the suspected product and seek appropriate medical advice.

RECOMMENDED DECISION SEQUENCE

1. Collect age and sex.
2. Collect actual skin type: oily, dry, combination, normal,
   sensitive-feeling.
3. Collect main concern(s) and location.
4. Ask severity questions: mild vs painful/deep/widespread/scarring/
   bleeding/oozing.
5. Ask about sensitivity, known allergies and previous reactions.
6. Ask about current skincare products/actives to prevent ingredient
   duplication and over-exfoliation.
7. For users who may be pregnant or trying to conceive, apply
   pregnancy ingredient safety rules before product matching.
8. Run the red-flag screen. If positive, pause product
   recommendations and show dermatologist guidance.
9. Map eligible concerns to ingredient categories.
10. Filter the product database by concern, skin type, age-safety,
    contraindications and ingredient duplication.
11. Rank a small routine rather than many actives: cleanser, then a
    targeted treatment if needed, then moisturizer, then sunscreen.
12. Provide patch-test/start-slow instructions and a review point. If
    symptoms worsen or do not improve, advise professional care.

RED-FLAG ESCALATION RULES

Pause recommendations and present dermatologist guidance if any of
the following are detected:

- Deep, painful acne nodules/cysts; acne causing scars; or extensive
  face/chest/back acne.
- Rapidly spreading, very painful, blistering, crusting, oozing or
  infected-looking rash.
- Severe or persistent itch, especially in older adults or when
  unexplained.
- Persistent eczema-, psoriasis-, rosacea- or
  seborrhoeic-dermatitis-like symptoms.
- A mole/spot/growth that changes, looks different from others,
  bleeds, repeatedly crusts, or does not heal.
- Rough/scaly sun-exposed lesion suspicious for actinic keratosis.
- Non-healing wound or chronic ulcer.
- Sudden unexplained hair loss.
- Facial swelling, eye swelling, breathing difficulty, fever with
  rash, or other urgent systemic symptoms — seek urgent medical care.

CONCERN TAXONOMY

Blackheads/whiteheads, mild pimples, post-acne dark marks, mild dark
spots/uneven tone, dullness/rough texture, fine lines/wrinkles, oily,
dry, combination, clogged/enlarged-looking pores, sun-protection
support — these may proceed in the product engine with their listed
qualifiers (for example: escalate deep/painful/scarring acne; avoid
over-stripping oily skin; escalate changing or unexplained lesions).

Mild irritation/sensitivity: simplify routine and stop suspected
irritants before matching.

Persistent disease-like rash, cyst/nodule/scarring acne, changing/
bleeding/non-healing lesion — do not enter the product engine; refer
for professional assessment.

AGE AND SEX GUIDANCE

Age and sex are contextual ranking signals, not deterministic rules.
Individual skin type and symptoms are more important. Use sex as a
modifier, not a fixed filter. Male skin often has higher sebum, but
the user's measured or reported skin type should override gender
assumptions.

12-19 (all): Puberty-driven sebum, clogged pores, mild acne,
irritation and product misuse dominate. Match gentle cleansing,
barrier support, mild acne care and SPF; escalate cysts/nodules,
scarring, severe rash or infection. Do not push young skin into
aggressive anti-ageing routines or routine strong retinol/
high-strength acids. Adolescent males may have more inflammatory
acne; severity overrides age-based matching.

20-29: Persistent or late-onset acne (especially in women), post-acne
pigmentation, uneven tone, early photoaging, dehydration and texture
become relevant. Match acne-compatible cleansing, salicylic acid or
benzoyl peroxide where suitable, azelaic acid/niacinamide, vitamin C
for tone if tolerated, SPF 30-50. Refer deep painful jawline
nodules, scarring or persistent moderate/severe acne, acne with
irregular periods or excess facial hair, severe allergic dermatitis,
and persistent rosacea-like redness. In men, also consider shaving
and ingrown-hair/razor irritation, and do not treat beard-area
flaking as simple dryness.

30-39: Adult acne plus pigmentation and early ageing. A "main concern
first" system is preferable to stacking multiple strong actives.
Match azelaic acid/niacinamide, vitamin C, retinol/retinoid only
after safety screening (including pregnancy), ceramide-rich
moisturizers, hyaluronic acid, SPF. Refer persistent melasma or
rosacea-like redness, deep acne scars, changing lesions and
persistent unexplained pigmentation.

40-59: Photoaging, dryness, pigmentation, wrinkles, uneven tone.
Match barrier care, SPF 30-50, retinol/retinoid where appropriate,
niacinamide, vitamin C, gentle AHA only if barrier is healthy. Refer
actinic keratosis-like or changing/bleeding lesions, sudden
unexplained pigmentation, severe persistent redness, non-healing
sores. Menopause-related dryness can make previously tolerated
actives irritating; check barrier status.

60+ (all): Thin, dry, fragile, easily irritated skin. Simplicity and
barrier protection outrank aggressive anti-ageing treatment. Match
fragrance-free gentle cleansing, rich moisturizers, ceramides,
glycerin and other barrier-supporting ingredients, SPF 30-50, and use
actives very cautiously. Refer severe unexplained itching, non-healing
lesions, deep cracks/bleeding, infection, changing/bleeding lesions,
and suspicious lesions. For men, dry exposed scalp and cumulative
photoaging matter; give especially clear lesion-screening guidance.

SUGGESTED USER MESSAGE STANDARD

"Clearer, healthier-looking skin starts with choosing products that
fit your skin - not simply following trends. WeGlow uses your age,
skin type, concerns and individual needs to suggest suitable skincare
while helping you avoid unnecessary or potentially irritating
products. If your answers suggest a concern that may need medical
assessment, WeGlow will recommend professional dermatological care
instead of trying to replace it."

EVIDENCE AND LIMITATIONS

Base advice on supplied evidence and research. Not every concern has
high-quality prevalence data for every exact age by sex cell; treat
age/sex matrices as an evidence-informed design synthesis, not a
diagnostic prevalence table. Recognize that product concentrations
and availability vary by country, and account for skin tone, climate,
medications, pregnancy, underlying disease and individual sensitivity.
`

function buildUserContextBlock(profile: Profile | null): string {
  if (!profile) {
    return (
      "USER INFORMATION\n\n" +
      "No WEGlow profile information is available for this user. " +
      "Do not assume a skin type, concerns, or any other detail — " +
      "ask before giving personalized advice that depends on it."
    )
  }

  const field = (label: string, value: string | boolean | null | undefined): string =>
    value === null || value === undefined || value === ""
      ? `${label}: not provided`
      : `${label}: ${value}`

  const lines = [
    field("Full name", profile.full_name),
    field("Age range", profile.age_range),
    field("Gender", profile.gender),
    field("Skin type", profile.skin_type),
    field("Face shape", profile.face_shape),
    field("Latest skin scan (concerns)", profile.skin_concerns),
    field("Last scan date/time", profile.last_scan_at),
    field("Date of birth", profile.date_of_birth),
    field(
      "Sensitive skin",
      profile.is_skin_sensitive === true
        ? "yes"
        : profile.is_skin_sensitive === false
        ? "no"
        : null,
    ),
    field("UV index", profile.uv_index),
    field("UV category", profile.uv_category),
    field("Humidity", profile.humidity),
    field("Location", profile.location_name),
    field("Last env reading", profile.last_env_at),
  ]

  return "USER INFORMATION\n\n" + lines.join("\n")
}

function cleanResponse(text: string): string {
  return text
    .replace(/^[ \t]*#{1,6}[ \t]*/gm, "")
    .replace(/\*\*/g, "")
    .replace(/__/g, "")
    .replace(/`/g, "")
    .replace(/^[ \t]*[*\-][ \t]+/gm, "")
    .replace(/\n{3,}/g, "\n\n")
    .trim()
}

function json(body: unknown, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...corsHeaders,
      ...init?.headers,
    },
  })
}

function error(status: number, message: string): Response {
  return json({ error: message }, { status })
}

async function generateGeminiResponse(
  contents: GeminiTurn[],
  systemInstruction: string,
): Promise<string> {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": GEMINI_API_KEY ?? "",
    },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: systemInstruction }] },
      contents: contents.map((turn) => ({ role: turn.role, parts: [{ text: turn.text }] })),
    }),
  })

  if (!res.ok) {
    throw new Error(`Gemini returned HTTP ${res.status}`)
  }

  const data = await res.json()
  const parts: unknown = data?.candidates?.[0]?.content?.parts
  if (!Array.isArray(parts)) {
    throw new Error("Gemini response did not contain a text candidate")
  }
  return parts
    .map((part) => (typeof part?.text === "string" ? part.text : ""))
    .join("")
}

interface SupabaseUser {
  id: string
}

async function getSupabaseUser(authorization: string): Promise<SupabaseUser | null> {
  if (!SUPABASE_URL.startsWith("https://") || !SUPABASE_PUBLISHABLE_KEY) {
    throw new Error("Supabase is not configured (missing SUPABASE_URL / SUPABASE_ANON_KEY).")
  }
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: {
      Authorization: authorization,
      apikey: SUPABASE_PUBLISHABLE_KEY,
    },
  })
  if (!res.ok) return null
  const body = await res.json()
  return typeof body?.id === "string" ? { id: body.id } : null
}

async function fetchProfile(userId: string, authorization: string): Promise<Profile | null> {
  try {
    const url = `${SUPABASE_URL}/rest/v1/profiles?${new URLSearchParams({
      id: `eq.${userId}`,
      select: PROFILE_FIELDS,
    })}`
    const res = await fetch(url, {
      headers: {
        Authorization: authorization,
        apikey: SUPABASE_PUBLISHABLE_KEY,
        Accept: "application/json",
      },
    })
    if (!res.ok) return null
    const rows = await res.json()
    return Array.isArray(rows) && rows.length > 0 ? (rows[0] as Profile) : null
  } catch {
    return null
  }
}

async function fetchProducts(authorization: string): Promise<any[]> {
  if (!SUPABASE_URL.startsWith("https://") || !SUPABASE_PUBLISHABLE_KEY) return []
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/products?select=*&limit=200`, {
      headers: {
        Authorization: authorization,
        apikey: SUPABASE_PUBLISHABLE_KEY,
        Accept: "application/json",
      },
    })
    if (!res.ok) return []
    const data = await res.json()
    return Array.isArray(data) ? data : []
  } catch {
    return []
  }
}

async function fetchHairstyles(authorization: string): Promise<any[]> {
  if (!SUPABASE_URL.startsWith("https://") || !SUPABASE_PUBLISHABLE_KEY) return []
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/hairstyles?select=*&limit=200`, {
      headers: {
        Authorization: authorization,
        apikey: SUPABASE_PUBLISHABLE_KEY,
        Accept: "application/json",
      },
    })
    if (!res.ok) return []
    const data = await res.json()
    return Array.isArray(data) ? data : []
  } catch {
    return []
  }
}

function buildProductsBlock(products: any[]): string {
  if (products.length === 0) return ""
  const lines = ["PRODUCT CATALOG"]
  products.forEach((p, i) => {
    const name = p.name ?? p.product_name ?? "Unknown"
    const brand = p.brand_name ?? p.brand ?? ""
    const price = p.price_label ?? p.price_lkr ?? p.price ?? ""
    const skinType = p.target_skin_type ?? ""
    const concerns = p.target_concerns ?? ""
    const category = p.category ?? ""
    const desc = p.description ?? ""
    const parts = [`${i + 1}. ${name}`]
    if (brand) parts.push(`Brand: ${brand}`)
    if (price) parts.push(`Price: ${price}`)
    if (category) parts.push(`Category: ${category}`)
    if (skinType) parts.push(`Suitable for: ${skinType}`)
    if (concerns) parts.push(`Targets: ${concerns}`)
    if (desc) parts.push(`Description: ${desc}`)
    lines.push(parts.join(" "))
  })
  return lines.join("\n")
}

function buildHairstylesBlock(hairstyles: any[]): string {
  if (hairstyles.length === 0) return ""
  const lines = ["HAIRSTYLE CATALOG"]
  hairstyles.forEach((h, i) => {
    const name = h.hairstyle_name ?? h.name ?? "Unknown"
    const length = h.hair_length ?? ""
    const faceShape = h.face_shape ?? ""
    const gender = h.gender ?? ""
    const imageUrl = h.image_url ?? ""
    const parts = [`${i + 1}. ${name}`]
    if (length) parts.push(`Length: ${length}`)
    if (faceShape) parts.push(`Face shape: ${faceShape}`)
    if (gender) parts.push(`Gender: ${gender}`)
    if (imageUrl) parts.push(`Image: ${imageUrl}`)
    lines.push(parts.join(" "))
  })
  return lines.join("\n")
}

async function handleChat(req: Request): Promise<Response> {
  if (!GEMINI_API_KEY || GEMINI_API_KEY === "REPLACE_WITH_YOUR_GEMINI_API_KEY") {
    return error(503, "Chat is temporarily unavailable.")
  }

  const authorization = req.headers.get("Authorization") ?? ""
  const user = await getSupabaseUser(authorization)
  if (!user) {
    return error(401, "Sign in before chatting.")
  }

  let payload: unknown
  try {
    payload = await req.json()
  } catch {
    return error(400, "Invalid JSON body.")
  }

  const body = payload as { message?: unknown; history?: unknown }

  const message = typeof body.message === "string" ? body.message.trim() : ""
  if (message.length === 0) {
    return error(400, "Message must not be empty.")
  }
  if (message.length > MAX_MESSAGE_LENGTH) {
    return error(400, "Message is too long.")
  }

  const rawHistory: unknown = Array.isArray(body.history) ? body.history : []
  const history: GeminiTurn[] = (rawHistory as { role?: unknown; text?: unknown }[])
    .filter(
      (turn) =>
        typeof turn === "object" &&
        turn !== null &&
        (turn.role === "user" || turn.role === "model") &&
        typeof turn.text === "string",
    )
    .map((turn) => ({ role: turn.role as "user" | "model", text: turn.text as string }))
    .slice(-MAX_HISTORY_TURNS)

  const profile = await fetchProfile(user.id, authorization)
  const products = await fetchProducts(authorization)
  const hairstyles = await fetchHairstyles(authorization)

  const contextParts = [WEGLOW_SYSTEM_INSTRUCTION.trim(), buildUserContextBlock(profile)]
  const productsBlock = buildProductsBlock(products)
  if (productsBlock) contextParts.push(productsBlock)
  const hairstylesBlock = buildHairstylesBlock(hairstyles)
  if (hairstylesBlock) contextParts.push(hairstylesBlock)

  const systemInstruction = contextParts.join("\n\n")

  const contents: GeminiTurn[] = [
    ...history,
    { role: "user", text: message },
  ]

  const text = await generateGeminiResponse(contents, systemInstruction)
  return json({ response: cleanResponse(text) })
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders, status: 204 })
  }
  if (req.method !== "POST") {
    return error(405, "Method not allowed.")
  }

  try {
    return await handleChat(req)
  } catch (err) {
    console.error("Chat function failed", err)
    return json(
      { error: "Chat is temporarily unavailable.", detail: err instanceof Error ? err.message : String(err) },
      { status: 500 },
    )
  }
})