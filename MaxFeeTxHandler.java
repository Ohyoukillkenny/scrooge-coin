/**
 * Created by konglingkun on 12/2/17.
 */
import java.security.PublicKey;
import java.util.*;

public class MaxFeeTxHandler {

    private UTXOPool myUTXOPool;

    public MaxFeeTxHandler(UTXOPool utPool) {
        this.myUTXOPool = new UTXOPool(utPool);
    }

    public boolean isValidTx(Transaction tx) {
        HashSet<UTXO> knownUTXO = new HashSet<UTXO>();
        int index = 0;
        double V_In = 0.0;
        double V_out = 0.0;
        for(Transaction.Input inp : tx.getInputs()){
            UTXO ut = new UTXO(inp.prevTxHash, inp.outputIndex);
            if (!this.myUTXOPool.contains(ut)) return false;
            PublicKey prevSig = this.myUTXOPool.getTxOutput(ut).address;
            Crypto myCript = new Crypto();
            if(!myCript.verifySignature(prevSig, tx.getRawDataToSign(index), inp.signature)) return false;
            if (knownUTXO.contains(ut)) return false;
            knownUTXO.add(ut);
            double V_prevOut = this.myUTXOPool.getTxOutput(ut).value;
            V_In += V_prevOut;
            index ++;
        }
        for(Transaction.Output oup : tx.getOutputs()){
            if(oup.value < 0.0) return false;
            V_out += oup.value;
        }
        return V_out <= V_In;
    }

    private double calculateFee(Transaction tx) {
        double sumInputs = 0;
        double sumOutputs = 0;
        for (Transaction.Input in : tx.getInputs()) {
            UTXO ut = new UTXO(in.prevTxHash, in.outputIndex);
            if (!myUTXOPool.contains(ut) || !isValidTx(tx)) continue;
            Transaction.Output txOutput = myUTXOPool.getTxOutput(ut);
            sumInputs += txOutput.value;
        }
        for (Transaction.Output out : tx.getOutputs()) {
            sumOutputs += out.value;
        }
        return sumInputs - sumOutputs;
    }

    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        /** Sort txs by descending fees*/
        Set<Transaction> txsSortedByFees = new TreeSet<>((tx1, tx2) -> {
            double tx1Fees = calculateFee(tx1);
            double tx2Fees = calculateFee(tx2);
            return Double.valueOf(tx2Fees).compareTo(tx1Fees);
        });
        Collections.addAll(txsSortedByFees, possibleTxs);
        Set<Transaction> acceptedTxs = new HashSet<>();
        while (true){
            Set<Transaction> tempTxs = txsSortedByFees;
            int cnt = tempTxs.size();
            for (Transaction tx : tempTxs) {
                if (isValidTx(tx)) {
                    acceptedTxs.add(tx);
                    updatePool(tx);
                    txsSortedByFees.remove(tx);
                    break;
                }
                else {cnt --;}
            }
            if(cnt == 0) break; // cnt == 0 means there is no valid tx.
        }
        Transaction[] validTxArray = new Transaction[acceptedTxs.size()];
        return acceptedTxs.toArray(validTxArray);
    }
    private void updatePool(Transaction tx){
        // remove old uts in myUTXOPool
        for (Transaction.Input inp : tx.getInputs()){
            UTXO ut = new UTXO(inp.prevTxHash, inp.outputIndex);
            this.myUTXOPool.removeUTXO(ut);
        }
        // add new uts in myUTXOPool
        byte[] txHash = tx.getHash();
        int index = 0;
        for (Transaction.Output oup : tx.getOutputs()){
            UTXO ut = new UTXO(txHash, index);
            index ++;
            this.myUTXOPool.addUTXO(ut,oup);
        }
    }
}
