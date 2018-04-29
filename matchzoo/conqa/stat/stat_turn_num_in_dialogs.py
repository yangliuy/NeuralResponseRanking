'''
Compute statistics about the number of turns in conversations
@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
'''
import numpy as np
from tqdm import tqdm

if __name__ == '__main__':
    basedir = '../../../data/ms_v2/ModelInput/'

    for data_part in list(['train', 'valid', 'test']):
        ori_file = basedir + data_part + '_rexpand_ctab_bodyText.txt'
        # format label \t turn1 \t turn2 .... \t turn_{t-1} \t response
        line_count = 0
        turn_nums = []
        with open(ori_file) as f_in:
            print 'cur_file: ', ori_file
            for l in tqdm(f_in):
                tokens = l.split('\t');
                turn_nums.append(len(tokens) - 2)
                # if (len(tokens) - 2 == 12):
                #     print 'test found 12 utterances in ',  tokens
            turn_nums_npa = np.array(turn_nums)
            print "%d\t%d\t%.2f\t%d" % (np.max(turn_nums_npa), np.min(turn_nums_npa), np.mean(turn_nums_npa), np.median(turn_nums_npa))

