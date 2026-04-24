import java.io.*;
import java.net.*;
import java.util.*;

public class QuizLeaderboard {

    static final String REG = "RA2311003011218";
    static final String BASE = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";

    public static void main(String[] args) throws Exception {

        Set<String> done = new HashSet<>();
        Map<String, Integer> totals = new LinkedHashMap<>();

        for (int i = 0; i <= 9; i++) {
            String url = BASE + "/quiz/messages?regNo=" + REG + "&poll=" + i;
            System.out.println("poll " + i + " -> calling api");
            String res = doGet(url);
            handleResponse(res, done, totals);
            if (i != 9) {
                System.out.println("sleeping 5 sec...");
                Thread.sleep(5000);
            }
        }

        List<Map.Entry<String, Integer>> list = new ArrayList<>(totals.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());

        int overall = 0;
        System.out.println("\nfinal scores:");
        for (Map.Entry<String, Integer> e : list) {
            System.out.println(e.getKey() + " : " + e.getValue());
            overall += e.getValue();
        }
        System.out.println("total = " + overall);

        StringBuilder body = new StringBuilder();
        body.append("{\"regNo\":\"").append(REG).append("\",\"leaderboard\":[");
        for (int i = 0; i < list.size(); i++) {
            Map.Entry<String, Integer> e = list.get(i);
            body.append("{\"participant\":\"").append(e.getKey())
                .append("\",\"totalScore\":").append(e.getValue()).append("}");
            if (i < list.size() - 1) body.append(",");
        }
        body.append("]}");

        System.out.println("\nsubmitting...");
        String resp = doPost(BASE + "/quiz/submit", body.toString());
        System.out.println("server said: " + resp);
    }

    static void handleResponse(String json, Set<String> done, Map<String, Integer> totals) {
        int start = json.indexOf("\"events\"");
        if (start == -1) return;

        int arrOpen = json.indexOf("[", start);
        int arrClose = json.lastIndexOf("]");
        if (arrOpen == -1 || arrClose == -1) return;

        String block = json.substring(arrOpen + 1, arrClose).trim();
        if (block.isEmpty()) return;

        String[] parts = block.split("\\},\\s*\\{");
        for (String part : parts) {
            String rid = grabString(part, "roundId");
            String name = grabString(part, "participant");
            String sc = grabNum(part, "score");
            if (rid.isEmpty() || name.isEmpty() || sc.isEmpty()) continue;

            String key = rid + "_" + name;
            if (!done.contains(key)) {
                done.add(key);
                totals.merge(name, Integer.parseInt(sc), Integer::sum);
                System.out.println("  ok: " + name + " +" + sc + " (round " + rid + ")");
            } else {
                System.out.println("  skip duplicate: " + key);
            }
        }
    }

    static String grabString(String s, String key) {
        int k = s.indexOf("\"" + key + "\"");
        if (k == -1) return "";
        int col = s.indexOf(":", k);
        int q1 = s.indexOf("\"", col + 1);
        int q2 = s.indexOf("\"", q1 + 1);
        if (q1 == -1 || q2 == -1) return "";
        return s.substring(q1 + 1, q2);
    }

    static String grabNum(String s, String key) {
        int k = s.indexOf("\"" + key + "\"");
        if (k == -1) return "";
        int col = s.indexOf(":", k);
        int i = col + 1;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        int j = i;
        while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '-')) j++;
        return s.substring(i, j).trim();
    }

    static String doGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        return read(c);
    }

    static String doPost(String url, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();
        return read(c);
    }

    static String read(HttpURLConnection c) throws Exception {
        InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
