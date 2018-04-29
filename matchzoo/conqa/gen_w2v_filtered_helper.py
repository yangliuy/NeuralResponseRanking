import sys
import os

for data in list(['udc']):
      for wd in list([200]):
        cur_folder = '../../data/' + data +'/ModelInput/'
        cmd = 'python gen_w2v_filtered.py ' \
              + cur_folder + 'train_word2vec_mikolov_' + str(wd) +'d_no_readvocab.txt ' \
              + cur_folder + 'word_dict.txt ' \
              + cur_folder + 'cut_embed_mikolov_' + str(wd) + 'd_no_readvocab.txt'
        print cmd
        os.system(cmd)