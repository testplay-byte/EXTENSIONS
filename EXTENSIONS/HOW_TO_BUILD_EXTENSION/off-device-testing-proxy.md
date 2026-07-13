# Off-Device Testing Proxy — Setup Guide

> **How to set up a proxy so the AI can test animepahe.pw + kwik.cx directly, without you having
> to share logcat/HTML back and forth.** This lets the AI fetch pages, verify selectors, and debug
> extraction code in real-time.
>
> **Recommended approach: Cloudflare Worker** (free, 5-minute setup, works from any IP).

---

## Why we need this

animepahe.pw and kwik.cx are behind Cloudflare's managed challenge. The AI's server gets 403
because Cloudflare validates the solver's IP address. A Cloudflare Worker runs ON Cloudflare's edge
network, so it's NOT challenged — it can fetch any URL freely.

## Option 1: Cloudflare Worker (RECOMMENDED — easiest + most reliable)

### What it does

A Cloudflare Worker is a tiny JavaScript function that runs on Cloudflare's servers. We deploy one
that acts as a proxy: it receives a URL from the AI, fetches it from Cloudflare's edge, and returns
the response. Since the Worker is ON Cloudflare, it bypasses the Cloudflare challenge.

### Step-by-step setup (5 minutes)

1. **Go to Cloudflare Workers:** https://dash.cloudflare.com/?to=/:account/workers
   - Sign in (or create a free account — no credit card needed)
   - Click "Create application" → "Create Worker"

2. **Name the worker:** `animepahe-proxy` (or any name)

3. **Paste this code** into the editor (replace the default code):

```javascript
export default {
  async fetch(request) {
    const url = new URL(request.url);
    const target = url.searchParams.get('url');
    
    if (!target) {
      return new Response('Usage: ?url=https://example.com', { status: 400 });
    }
    
    try {
      // Forward the request to the target URL
      const headers = new Headers();
      headers.set('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
      headers.set('Referer', 'https://animepahe.pw/');
      
      // Copy the target URL's origin as Referer if it's kwik.cx
      if (target.includes('kwik.cx')) {
        headers.set('Referer', 'https://animepahe.pw/');
      }
      
      const response = await fetch(target, { headers });
      const body = await response.text();
      
      // Return the response with CORS headers so the AI can fetch it
      return new Response(body, {
        status: response.status,
        headers: {
          'Content-Type': response.headers.get('Content-Type') || 'text/html',
          'Access-Control-Allow-Origin': '*',
        },
      });
    } catch (e) {
      return new Response('Error: ' + e.message, { status: 500 });
    }
  }
};
```

4. **Click "Save and Deploy"**

5. **Copy the Worker URL** — it looks like:
   `https://animepahe-proxy.your-username.workers.dev`

6. **Share the Worker URL with the AI** — paste it in the chat.

### How the AI uses it

The AI fetches: `https://animepahe-proxy.your-username.workers.dev?url=https://kwik.cx/e/SOME_ID`

The Worker fetches the kwik.cx page from Cloudflare's edge (no challenge) and returns the HTML.
The AI can then:
- Verify the play page selectors
- See the actual kwik.cx packed JS
- Test the Dean Edwards unpacker against real data
- Debug extraction issues directly

### Security notes

- The Worker is public (anyone with the URL can use it). This is fine for testing — just delete it
  when you're done: Cloudflare Dashboard → Workers → `animepahe-proxy` → Delete.
- The Worker only does GET requests with a fixed User-Agent. It can't POST or send cookies.
- Free tier: 100,000 requests/day — more than enough for testing.

---

## Option 2: Python script on your machine (alternative)

If you don't want to use Cloudflare Workers, you can run a Python script on your machine that
fetches pages and saves them. The AI tells you which URLs to fetch, you run the script, and share
the results.

### Setup

1. **Install Python** (if not already installed): https://www.python.org/downloads/

2. **Install curl_cffi** (Chrome TLS impersonation):
```bash
pip install curl_cffi
```

3. **Save this script** as `fetch_kwik.py`:

```python
from curl_cffi import requests
import sys

# Your cf_clearance cookies (from browser DevTools)
CF_CLEARANCE_ANIMEPAHE = "YOUR_ANIMEPAHE_CF_CLEARANCE_HERE"
CF_CLEARANCE_KWIK = "YOUR_KWIK_CF_CLEARANCE_HERE"
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

def fetch(url):
    # Choose the right cookie based on the domain
    if "animepahe" in url:
        cookie = f"cf_clearance={CF_CLEARANCE_ANIMEPAHE}"
    elif "kwik.cx" in url:
        cookie = f"cf_clearance={CF_CLEARANCE_KWIK}"
    else:
        cookie = ""
    
    resp = requests.get(url, impersonate='chrome', headers={
        'User-Agent': USER_AGENT,
        'Cookie': cookie,
        'Referer': 'https://animepahe.pw/',
    })
    
    print(f"Status: {resp.status_code}")
    print(f"Length: {len(resp.text)}")
    print("---")
    
    if resp.status_code == 200:
        # Save to file
        filename = url.split('/')[-1] + '.html'
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(resp.text)
        print(f"Saved to: {filename}")
    else:
        print(f"Blocked! Response: {resp.text[:200]}")

if __name__ == '__main__':
    url = sys.argv[1] if len(sys.argv) > 1 else 'https://kwik.cx/e/test'
    fetch(url)
```

4. **Update the cookies** — replace `YOUR_ANIMEPAHE_CF_CLEARANCE_HERE` and
   `YOUR_KWIK_CF_CLEARANCE_HERE` with the cf_clearance values from your browser.

5. **Run it:**
```bash
python fetch_kwik.py "https://kwik.cx/e/aqUFKLynSAUE"
```

6. **Share the saved HTML file** with the AI.

### Limitation

cf_clearance cookies expire after a few hours and are IP-specific. You'll need to refresh them
each testing session. The Cloudflare Worker approach doesn't have this limitation.

---

## Which option to choose?

| | Cloudflare Worker | Python script |
|---|---|---|
| **Setup time** | 5 minutes | 5 minutes |
| **AI can test directly** | ✅ Yes (just needs the URL) | ❌ No (you run it, share results) |
| **Cookie expiry** | ✅ Never (runs on CF edge) | ❌ Expires every few hours |
| **Cost** | Free (100k req/day) | Free |
| **Reliability** | ✅ High (always works) | ⚠️ Depends on cookie freshness |
| **Best for** | Ongoing development | One-time debugging |

**Recommendation:** Use the Cloudflare Worker. It's the easiest, most reliable, and lets the AI
test directly without you being a middleman.
