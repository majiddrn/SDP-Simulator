import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Write entity id");
        int id = scanner.nextInt();

        Process p = new ProcessBuilder("hostname", "-I").start();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String ips = bufferedReader.readLine();

        DomainParticipant domainParticipant = new DomainParticipant(ips, id, "DP");
        domainParticipant.enable();
    }
}